import { Graph, Node, Point } from '@antv/x6';
import { NodeTypeEnum } from './data/enumeration';
import { CustomX6NodeProxy } from './data/custom-x6-node-proxy';

export class WorkflowValidator {
  private readonly graph: Graph;
  private readonly proxy: any;

  constructor(graph: Graph, proxy: any) {
    this.graph = graph;
    this.proxy = proxy;
  }

  /**
   * 校验所有节点
   * @throws 尚未通过校验时，抛异常
   */
  async checkNodes(): Promise<void> {
    const workflowNodes = this.graph.getNodes().map(node => new CustomX6NodeProxy(node).getData());

    for (const workflowNode of workflowNodes) {
      await workflowNode.validate();
    }
  }

  checkDroppingNode(node: Node, mousePosition: Point.PointLike, nodePanelRect: DOMRect): boolean {
    if (!this.checkDroppingPosition(mousePosition, nodePanelRect)) {
      return false;
    }

    if (!this.checkTrigger(node)) {
      return false;
    }

    return true;
  }

  private checkDroppingPosition(mousePosition: Point.PointLike, nodePanelRect: DOMRect): boolean {
    const { x: mousePosX, y: mousePosY } = mousePosition;
    const { x, y, width, height } = nodePanelRect;
    const maxX = x + width;
    const maxY = y + height;

    if (mousePosX >= x && mousePosX <= maxX &&
      mousePosY >= y && mousePosY <= maxY) {
      // 在节点面板中拖放时，失败
      return false;
    }

    return true;
  }

  private checkTrigger(droppingNode: Node): boolean {
    const proxy = new CustomX6NodeProxy(droppingNode);
    const data = proxy.getData();

    if (![NodeTypeEnum.CRON, NodeTypeEnum.WEBHOOK].includes(data.getType())) {
      // 非trigger时，忽略
      return true;
    }

    // 表示当前拖放的节点为trigger
    const currentTrigger = this.graph.getNodes().find(node => {
      const proxy = new CustomX6NodeProxy(node);
      return [NodeTypeEnum.CRON, NodeTypeEnum.WEBHOOK].includes(proxy.getData().getType());
    });

    if (currentTrigger) {
      this.proxy.$warning('只能有一个触发器节点');
      return false;
    }

    return true;
  }
}