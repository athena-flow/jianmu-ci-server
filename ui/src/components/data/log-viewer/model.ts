import { ILogVo } from '@/api/dto/workflow-execution-record';
// @ts-ignore
import _listen from 'good-listener';

export const MAX_SIZE = 1000;
export type DownloadFnType = () => Promise<string>;
export type LoadMoreFnType = (line: number, size: number) => Promise<ILogVo[]>;
export type CallBackFnType = (data: string[], startLine?: number) => void;
export type CheckAutoScrollFnType = () => boolean;
export type HandleAutoScrollFnType = (scrollFlag: boolean) => void;

export default class LogViewer {
  private readonly el: HTMLElement;
  private readonly filename: string;
  private readonly value: string;
  private readonly isSse: boolean;
  private readonly downloadFn?: DownloadFnType;
  private readonly loadMoreFn?: LoadMoreFnType;
  private readonly virtualNoDiv: HTMLDivElement;
  private line: number;
  private readonly lines: number[];
  private readonly eventSource: any;
  private readonly callbackFn: CallBackFnType;
  private readonly checkAutoScrollFn: CheckAutoScrollFnType;
  private readonly mutationObserver: MutationObserver;
  private readonly handleAutoScrollFn: HandleAutoScrollFnType;
  private readonly listenScroll: any;

  constructor(el: HTMLElement, filename: string, value: string, url: string, downloadFn: DownloadFnType | undefined, loadMoreFn: LoadMoreFnType | undefined,
    callbackFn: CallBackFnType, checkAutoScrollFn: CheckAutoScrollFnType, handleAutoScrollFn: HandleAutoScrollFnType) {
    this.el = el;
    this.filename = filename;
    this.value = url || value;
    this.isSse = !!url;
    this.downloadFn = downloadFn;
    this.loadMoreFn = loadMoreFn;

    this.virtualNoDiv = document.createElement('div');
    this.virtualNoDiv.style.position = 'fixed';
    this.virtualNoDiv.style.left = '-1000px';
    this.virtualNoDiv.style.top = '-1000px';
    this.virtualNoDiv.style.margin = '0px';
    this.virtualNoDiv.style.padding = '0px';
    this.virtualNoDiv.style.borderWidth = '0px';
    this.virtualNoDiv.style.height = '0px';
    this.virtualNoDiv.style.visibility = 'hidden';

    this.line = 0;
    this.lines = [];
    this.callbackFn = callbackFn;
    this.checkAutoScrollFn = checkAutoScrollFn;

    // 监听元素变化滚动
    this.mutationObserver = new MutationObserver(
      () => {
        if (!this.checkAutoScrollFn()) {
          return;
        }
        const contentEl = this.el.lastElementChild as HTMLElement;
        contentEl.scrollTop = contentEl.scrollHeight;
      },
    );
    this.mutationObserver.observe(this.el, {
      // 子节点变动(新增、删除、更改)
      childList: true,
      // 属性变动
      attributes: true,
      // 节点内容或节点文本变动
      characterData: true,
      // 是否将观察器应用于该节点的所有后代节点
      subtree: true,
    });

    // sse
    this.eventSource = this.isSse ? new EventSource(this.value + MAX_SIZE, { withCredentials: true }) : undefined;

    this.handleAutoScrollFn = handleAutoScrollFn;

    this.listenScroll = _listen(this.el.lastElementChild, 'scroll', () => {
      this.handleAutoScrollFn(this.el.lastElementChild!.scrollHeight - this.el.lastElementChild!.scrollTop <= this.el.lastElementChild!.clientHeight);
    });
  }

  /**
   * 检查url或value
   * @param url
   * @param value
   */
  checkValue(url: string, value: string): boolean {
    if (this.isSse) {
      return this.value === url;
    } else {
      return this.value === value;
    }
  }

  /**
   * 判断是否显示加载更多
   */
  isMoreLog(): boolean {
    return this.line > 1;
  }

  /**
   * 销毁
   */
  destroy(): void {
    this.mutationObserver.disconnect();
    if (this.isSse) {
      this.eventSource.close();
    }
    this.listenScroll.destroy();
  }

  /**
   * 监听变化调用日志
   * @param data
   */
  listen(data: string[]): void {
    this.el.lastElementChild?.appendChild(this.virtualNoDiv);
    if (this.isSse) {
      this.eventSource.onmessage = async (e: any) => {
        this.lines.push(Number(e.lastEventId));
        data.push(e.data);
        if (this.line === 0) {
          this.line = this.lines[0];
        }
        this.callbackFn(data, this.line);
      };
      this.eventSource.onerror = (e: any) => {
        if (e.currentTarget.readyState === 0) {
          console.log('服务端已断开连接，禁止尝试重连');
          this.eventSource.close();
          return;
        }
        console.error(e);
      };
      return;
    }
    data = this.value.split(/\r?\n/);
    this.callbackFn(data);
  }

  /**
   * 计算line宽度
   * @param logLines
   */
  calculateTempNoWidth(logLines: number): number {
    this.virtualNoDiv.innerHTML = logLines + '';

    return this.virtualNoDiv.clientWidth + 25;
  }

  /**
   * 加载
   */
  async loadMore(): Promise<ILogVo[] | undefined> {
    if (!this.loadMoreFn) {
      return;
    }
    try {
      let size: number;
      if (this.line > MAX_SIZE) {
        this.line -= MAX_SIZE;
        size = MAX_SIZE;
      } else {
        size = this.line - 1;
        this.line = 1;
      }
      return await this.loadMoreFn(this.line, size);
    } catch (err) {
      console.warn(err.message);
    }
  }

  /**
   * 下载
   */
  async downLoad(): Promise<void> {
    try {
      const log = (this.downloadFn && this.isSse) ? await this.downloadFn() : this.value;
      const blob = new Blob([log]);
      const url = window.URL.createObjectURL(blob);

      const a = document.createElement('a');
      a.href = url;
      a.download = this.filename;
      a.click();

      // 释放url
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.warn(err.message);
    }
  }

  /**
   * 复制
   * @param data
   * @param moreLog
   */
  copy(data: string[], moreLog: boolean): string[] | string {
    if (moreLog) {
      return data.join('\n');
    }
    if (this.isSse || this.downloadFn) {
      return data.join('\n');
    }
    return this.value;
  }
}