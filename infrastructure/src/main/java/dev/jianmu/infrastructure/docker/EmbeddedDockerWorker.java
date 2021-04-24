package dev.jianmu.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import dev.jianmu.task.aggregate.DockerTask;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @class: Client
 * @description: Docker客户端
 * @author: Ethan Liu
 * @create: 2021-04-13 10:59
 **/
@Service
@Profile("!test")
public class EmbeddedDockerWorker implements DockerWorker {
    private String dockerHost;
    private String apiVersion;
    private String registryUsername;
    private String registryPassword;
    private String registryEmail;
    private String registryUrl;
    private String dockerConfig;
    private String dockerCertPath;

    private Boolean dockerTlsVerify;

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedDockerWorker.class);
    private DockerClient dockerClient;
    private Map<String, Integer> runStatusMap = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher publisher;

    @Inject
    public EmbeddedDockerWorker(EmbeddedDockerWorkerProperties properties, ApplicationEventPublisher publisher) {
        this.dockerHost = properties.getDockerHost();
        this.apiVersion = properties.getApiVersion();
        this.registryUsername = properties.getRegistryUsername();
        this.registryPassword = properties.getRegistryPassword();
        this.registryEmail = properties.getRegistryEmail();
        this.registryUrl = properties.getRegistryUrl();
        this.dockerConfig = properties.getDockerConfig();
        this.dockerCertPath = properties.getDockerCertPath();
        this.dockerTlsVerify = properties.getDockerTlsVerify();
        this.publisher = publisher;
        this.connect();
    }

    private void connect() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(this.dockerHost)
                .withApiVersion(this.apiVersion)
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(100)
                .build();
        DockerClient dockerClient = DockerClientBuilder
                .getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();
        dockerClient.pingCmd().exec();
        this.dockerClient = dockerClient;
    }

    @Override
    @Async
    public void runTask(DockerTask dockerTask, BufferedWriter logWriter) {
        var spec = dockerTask.getSpec();
        // 创建容器参数
        var createContainerCmd = dockerClient.createContainerCmd(spec.getImage());
        if (!spec.getWorkingDir().isBlank()) {
            createContainerCmd.withWorkingDir(spec.getWorkingDir());
        }
        if (null != spec.getHostConfig()) {
            List<Mount> mounts = new ArrayList<>();
            spec.getHostConfig().getMounts().forEach(m -> {
                mounts.add(
                        new Mount()
                                .withType(MountType.VOLUME)
                                .withSource(m.getSource())
                                .withTarget(m.getTarget())
                );
            });
            var hostConfig = HostConfig.newHostConfig().withMounts(mounts);
            createContainerCmd.withHostConfig(hostConfig);
        }
        if (null != spec.getEnv()) {
            createContainerCmd.withEnv(spec.getEnv());
        }
        if (null != spec.getEntrypoint()) {
            createContainerCmd.withEntrypoint(spec.getEntrypoint());
        }
        if (null != spec.getCmd()) {
            createContainerCmd.withCmd(spec.getCmd());
        }
        var containerResponse = createContainerCmd.exec();
        // 启动容器
        this.dockerClient.startContainerCmd(containerResponse.getId()).exec();
        // 获取日志
        try {
            this.dockerClient.logContainerCmd(containerResponse.getId())
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTailAll()
                    .withFollowStream(true)
                    .exec(new ResultCallback.Adapter<>() {
                        @Override
                        public void onNext(Frame object) {
                            try {
                                logWriter.write(new String(object.getPayload(), StandardCharsets.UTF_8));
                                logWriter.flush();
                            } catch (IOException e) {
                                logger.error("获取容器日志异常:", e);
                            }
                        }
                    }).awaitCompletion();
        } catch (InterruptedException e) {
            logger.error("获取容器日志操作被中断:", e);
            try {
                logWriter.close();
            } catch (IOException ioException) {
                logger.error("日志流关闭失败:", e);
            }
            Thread.currentThread().interrupt();
        }
        // 等待容器执行结果
        try {
            this.dockerClient.waitContainerCmd(containerResponse.getId()).exec(new ResultCallback.Adapter<>() {
                @Override
                public void onNext(WaitResponse object) {
                    logger.info("dockerTask {} status code is: {}", dockerTask.getTaskInstanceId(), object.getStatusCode());
                    runStatusMap.put(dockerTask.getTaskInstanceId(), object.getStatusCode());
                }
            }).awaitCompletion();
        } catch (InterruptedException e) {
            logger.error("获取容器执行结果操作被中断:", e);
            Thread.currentThread().interrupt();
        }
        // 获取容器执行结果文件(JSON,非数组)，转换为任务输出参数
        int statusCode = 0;
        String resultFile = null;
        if (null != dockerTask.getResultFile()) {
            var stream = this.dockerClient.copyArchiveFromContainerCmd(containerResponse.getId(), dockerTask.getResultFile()).exec();
            var tarStream = new TarArchiveInputStream(stream);
            try (var reader = new BufferedReader(new InputStreamReader(tarStream, StandardCharsets.UTF_8))) {
                var tarArchiveEntry = tarStream.getNextTarEntry();
                if (!tarStream.canReadEntryData(tarArchiveEntry)) {
                    logger.info("不能读取tarArchiveEntry");
                }
                if (!tarArchiveEntry.isFile()) {
                    logger.info("执行结果文件必须是文件类型, 不支持目录或其他类型");
                }
                logger.info("tarArchiveEntry's name: {}", tarArchiveEntry.getName());
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("结果文件内容: {}", line);
                }
                resultFile = IOUtils.toString(reader);
            } catch (IOException e) {
                logger.error("无法获取容器执行结果文件:", e);
                statusCode = 1;
            }
        }
        // 清除容器
        this.dockerClient.removeContainerCmd(containerResponse.getId())
                .withRemoveVolumes(true)
                .exec();
        // 发送结果通知
        publisher.publishEvent(
                TaskResult.builder()
                        .taskId(dockerTask.getTaskInstanceId())
                        .cmdStatusCode(runStatusMap.get(dockerTask.getTaskInstanceId()))
                        .statusCode(statusCode)
                        .resultFile(resultFile)
                        .build()
        );
    }

    @Override
    public void createVolume(String volumeName) {
        // 创建Volume
        this.dockerClient.createVolumeCmd()
                .withName(volumeName)
                .withDriver("local")
                .exec();
    }

    @Override
    public void deleteVolume(String volumeName) {
        // 清除Volume
        this.dockerClient.removeVolumeCmd(volumeName).exec();
    }
}
