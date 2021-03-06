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

    // ????????????????????????
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
      // ???????????????(????????????????????????)
      childList: true,
      // ????????????
      attributes: true,
      // ?????????????????????????????????
      characterData: true,
      // ?????????????????????????????????????????????????????????
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
   * ??????url???value
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
   * ??????????????????????????????
   */
  isMoreLog(): boolean {
    return this.line > 1;
  }

  /**
   * ??????
   */
  destroy(): void {
    this.mutationObserver.disconnect();
    if (this.isSse) {
      this.eventSource.close();
    }
    this.listenScroll.destroy();
  }

  /**
   * ????????????????????????
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
          console.log('?????????????????????????????????????????????');
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
   * ??????line??????
   * @param logLines
   */
  calculateTempNoWidth(logLines: number): number {
    this.virtualNoDiv.innerHTML = logLines + '';

    return this.virtualNoDiv.clientWidth + 25;
  }

  /**
   * ??????
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
   * ??????
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

      // ??????url
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.warn(err.message);
    }
  }

  /**
   * ??????
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