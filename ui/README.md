#### 安装

``` bash
# 安装依赖
npm install 或 yarn

# 启动，http://localhost:3000/
npm run serve 或 yarn serve

# 生产环境构建
npm run build 或 yarn build
```

#### 构建docker镜像

```
# 创建docker镜像
docker build -t jianmudev/jianmu-ci-ui:${version} .

# 上传docker镜像
docker push jianmudev/jianmu-ci-ui:${version}
```

#### 开源协议
本项目基于MulanPubL-2.0协议。
