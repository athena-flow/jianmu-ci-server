import { Graph, Point } from '@antv/x6';
import { ZoomTypeEnum } from '@/components/workflow/workflow-editor/model/enumeration';

const MIN_ZOOM = 0.2;
const MAX_ZOOM = 5;
// 缩放间隔
const ZOOM_INTERVAL = 0.1;

export class WorkflowTool {
  private readonly container: HTMLElement;
  private readonly graph: Graph;

  constructor(graph: Graph) {
    this.container = graph.container;
    this.graph = graph;
  }

  /**
   * 缩放
   * @param type
   */
  zoom(type: ZoomTypeEnum) {
    let factor = this.graph.zoom();

    switch (type) {
      case ZoomTypeEnum.IN:
        factor += ZOOM_INTERVAL;
        factor = factor > MAX_ZOOM ? MAX_ZOOM : factor;
        break;
      case ZoomTypeEnum.OUT:
        factor -= ZOOM_INTERVAL;
        factor = factor < MIN_ZOOM ? MIN_ZOOM : factor;
        break;
      case ZoomTypeEnum.FIT:
        factor = this.getFitFactor();
        break;
      // case ZoomTypeEnum.ORIGINAL:
      default:
        factor = 1;
        break;
    }

    this.zoomTo(factor);
  }

  /**
   * 缩放
   * @param factor
   * @private
   */
  private zoomTo(factor: number) {
    const currentFactor = this.graph.zoom();

    if (currentFactor === factor) {
      // 缩放比例相同时，忽略
      return;
    }

    this.fitCenter(factor);

    const center = this.getZoomCenter();
    this.graph.zoomTo(factor, { center });
  }

  /**
   * 获取缩放中心
   * @private
   */
  private getZoomCenter(): Point.PointLike {
    if (this.isGraphEmpty()) {
      const { x, y } = this.getContainerCenter();
      return { x, y };
    }
    const { x, y } = this.getContentCenter();
    return { x, y };
  }


  /**
   * 获取适配系数
   * @private
   */
  private getFitFactor(): number {
    if (this.isGraphEmpty()) {
      return 1;
    }

    // 缩放到最小，判断是否溢出
    if (this.checkMinContentOverflow()) {
      return MIN_ZOOM;
    }

    const { clientWidth: panelW, clientHeight: panelH } = this.container.parentElement!;
    const { width: contentW, height: contentH } = this.graph.getContentArea();

    const wRatio = panelW / contentW;
    const hRatio = panelH / contentH;

    return Math.min(wRatio, hRatio, MAX_ZOOM);
  }

  /**
   * 获取容器中心坐标，相对容器左上角
   * @private
   */
  private getContainerCenter(): Point.PointLike {
    const minX = 0;
    const minY = 0;
    const maxX = this.container.offsetWidth;
    const maxY = this.container.offsetHeight;

    return {
      x: (maxX - minX) / 2,
      y: (maxY - minY) / 2,
    };
  }

  /**
   * 获取内容中心坐标，相对容器左上角
   * @private
   */
  private getContentCenter(): Point.PointLike {
    const { x, y, width, height } = this.graph.getContentArea();

    return {
      x: width / 2 + x,
      y: height / 2 + y,
    };
  }

  /**
   * 画布内容是否为空
   * @private
   */
  private isGraphEmpty(): boolean {
    return this.graph.getNodes().length === 0;
  }

  /**
   * 检查最小缩放内容是否溢出，相对面板大小
   */
  private checkMinContentOverflow(): boolean {
    const { clientWidth: panelW, clientHeight: panelH } = this.container.parentElement!;
    const { width: contentW, height: contentH } = this.graph.getContentArea();

    const minContentW = contentW * MIN_ZOOM;
    const minContentH = contentH * MIN_ZOOM;

    return minContentW > panelW || minContentH > panelH;
  }

  /**
   * 内容移动到中心
   * @param factor
   * @private
   */
  private fitCenter(factor: number) {
    if (!this.isGraphEmpty()) {
      this.moveContentToContainerCenter(factor);
    }
    this.moveContainerToPanelCenter();
  }

  /**
   * 移动内容到容器中心
   * @param factor
   * @private
   */
  private moveContentToContainerCenter(factor: number) {
    const { x: containerX, y: containerY } = this.getContainerCenter();
    const { x: contentX, y: contentY } = this.getContentCenter();

    console.log(containerX, contentX);
    console.log(containerY, contentY);

    const moveX = Math.floor((containerX - contentX) / factor);
    const moveY = Math.floor((containerY - contentY) / factor);

    console.log(moveX, moveY);

    // 移动所有节点到容器中心
    this.graph.getNodes().forEach(node => node.translate(moveX, moveY));
  }

  /**
   * 移动容器到面板中心
   */
  private moveContainerToPanelCenter() {
    const containerParent = this.container.parentElement!;

    // 水平&垂直居中
    const x = (containerParent.clientWidth - this.container.offsetWidth) / 2;
    const y = (containerParent.clientHeight - this.container.offsetHeight) / 2;


    this.container.style.left = `${x}px`;
    this.container.style.top = `${y}px`;
  }
}