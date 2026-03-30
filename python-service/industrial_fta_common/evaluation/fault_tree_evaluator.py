# Fault Tree Evaluator
# 故障树评估引擎，用于对比 AI 生成的故障树与标准答案

from typing import Dict, List, Any, Optional, Tuple
from dataclasses import dataclass
from enum import Enum
import copy

class EvaluationMetrics(Enum):
    """评估指标枚举"""
    STRUCTURE_ACCURACY = "structure_accuracy"
    EVENT_PRECISION = "event_precision"
    EVENT_RECALL = "event_recall"
    EVENT_F1 = "event_f1"
    RELATION_ACCURACY = "relation_accuracy"
    GATE_ACCURACY = "gate_accuracy"
    OVERALL_SCORE = "overall_score"

@dataclass
class EvaluationResult:
    """评估结果"""
    tree_edit_distance: int
    structure_accuracy: float
    event_precision: float
    event_recall: float
    event_f1: float
    relation_accuracy: float
    gate_accuracy: float
    overall_score: float
    detailed_report: Dict[str, Any]
    missing_events: List[Dict[str, Any]]
    extra_events: List[Dict[str, Any]]
    wrong_relations: List[Dict[str, Any]]

class FaultTreeEvaluator:
    """故障树评估器"""

    def __init__(self):
        """初始化评估器"""
        self.weights = {
            'structure': 0.30,
            'event': 0.30,
            'relation': 0.40
        }

    def evaluate(
        self,
        generated_tree: Dict[str, Any],
        gold_standard_tree: Dict[str, Any]
    ) -> EvaluationResult:
        """
        评估生成的故障树

        参数:
            generated_tree: AI 生成的故障树
            gold_standard_tree: 黄金标准故障树

        返回:
            评估结果
        """
        generated_flat = self._flatten_tree(generated_tree)
        gold_standard_flat = self._flatten_tree(gold_standard_tree)

        tree_edit_distance, edit_operations = self._calculate_tree_edit_distance(
            generated_flat, gold_standard_flat
        )

        structure_accuracy = self._calculate_structure_accuracy(
            tree_edit_distance, len(gold_standard_flat)
        )

        event_metrics = self._calculate_event_metrics(
            generated_flat, gold_standard_flat
        )

        relation_metrics = self._calculate_relation_metrics(
            generated_tree, gold_standard_tree
        )

        overall_score = self._calculate_overall_score(
            structure_accuracy,
            event_metrics['f1'],
            relation_metrics['accuracy']
        )

        missing_events, extra_events, wrong_relations = self._analyze_differences(
            generated_flat, gold_standard_flat, generated_tree, gold_standard_tree
        )

        return EvaluationResult(
            tree_edit_distance=tree_edit_distance,
            structure_accuracy=structure_accuracy,
            event_precision=event_metrics['precision'],
            event_recall=event_metrics['recall'],
            event_f1=event_metrics['f1'],
            relation_accuracy=relation_metrics['accuracy'],
            gate_accuracy=relation_metrics['gate_accuracy'],
            overall_score=overall_score,
            detailed_report={
                'edit_operations': edit_operations,
                'total_generated_nodes': len(generated_flat),
                'total_gold_standard_nodes': len(gold_standard_flat),
                'weights': self.weights
            },
            missing_events=missing_events,
            extra_events=extra_events,
            wrong_relations=wrong_relations
        )

    def _flatten_tree(self, tree: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        将故障树扁平化为节点列表

        参数:
            tree: 故障树

        返回:
            节点列表
        """
        nodes = []

        def traverse(node: Dict[str, Any]):
            if not node:
                return

            node_info = {
                'event_id': node.get('event_id', ''),
                'event_name': node.get('event_name', ''),
                'event_type': node.get('event_type', ''),
                'gate_type': node.get('gate_type'),
                'parent_id': node.get('parent_id')
            }
            nodes.append(node_info)

            if 'children' in node and node['children']:
                for child in node['children']:
                    child['parent_id'] = node.get('event_id')
                    traverse(child)

        traverse(tree)
        return nodes

    def _calculate_tree_edit_distance(
        self,
        generated_nodes: List[Dict[str, Any]],
        gold_standard_nodes: List[Dict[str, Any]]
    ) -> Tuple[int, List[Dict[str, Any]]]:
        """
        计算树编辑距离

        参数:
            generated_nodes: 生成的节点列表
            gold_standard_nodes: 标准节点列表

        返回:
            (编辑距离, 编辑操作列表)
        """
        edit_distance = 0
        edit_operations = []

        generated_ids = {node['event_id'] for node in generated_nodes}
        gold_ids = {node['event_id'] for node in gold_standard_nodes}

        missing_ids = gold_ids - generated_ids
        extra_ids = generated_ids - gold_ids

        for node_id in missing_ids:
            edit_distance += 1
            edit_operations.append({
                'type': 'DELETE',
                'event_id': node_id,
                'description': f'缺少节点: {node_id}'
            })

        for node_id in extra_ids:
            edit_distance += 1
            edit_operations.append({
                'type': 'INSERT',
                'event_id': node_id,
                'description': f'多余节点: {node_id}'
            })

        for gen_node in generated_nodes:
            for gold_node in gold_standard_nodes:
                if gen_node['event_id'] == gold_node['event_id']:
                    if gen_node['event_type'] != gold_node['event_type']:
                        edit_distance += 1
                        edit_operations.append({
                            'type': 'MODIFY',
                            'event_id': gen_node['event_id'],
                            'from': gold_node['event_type'],
                            'to': gen_node['event_type'],
                            'description': f'节点类型错误: {gen_node["event_id"]}'
                        })

                    if gen_node.get('gate_type') != gold_node.get('gate_type'):
                        edit_distance += 1
                        edit_operations.append({
                            'type': 'MODIFY_GATE',
                            'event_id': gen_node['event_id'],
                            'from': gold_node.get('gate_type'),
                            'to': gen_node.get('gate_type'),
                            'description': f'逻辑门类型错误: {gen_node["event_id"]}'
                        })
                    break

        return edit_distance, edit_operations

    def _calculate_structure_accuracy(
        self,
        edit_distance: int,
        gold_standard_node_count: int
    ) -> float:
        """
        计算结构准确率

        参数:
            edit_distance: 编辑距离
            gold_standard_node_count: 标准树节点数

        返回:
            结构准确率
        """
        if gold_standard_node_count == 0:
            return 0.0
        accuracy = 1 - (edit_distance / gold_standard_node_count)
        return max(0.0, min(1.0, accuracy))

    def _calculate_event_metrics(
        self,
        generated_nodes: List[Dict[str, Any]],
        gold_standard_nodes: List[Dict[str, Any]]
    ) -> Dict[str, float]:
        """
        计算事件抽取指标

        参数:
            generated_nodes: 生成的节点列表
            gold_standard_nodes: 标准节点列表

        返回:
            包含 precision, recall, f1 的字典
        """
        generated_ids = {node['event_id'] for node in generated_nodes}
        gold_ids = {node['event_id'] for node in gold_standard_nodes}

        true_positives = len(generated_ids & gold_ids)
        false_positives = len(generated_ids - gold_ids)
        false_negatives = len(gold_ids - generated_ids)

        precision = true_positives / (true_positives + false_positives) if (true_positives + false_positives) > 0 else 0.0
        recall = true_positives / (true_positives + false_negatives) if (true_positives + false_negatives) > 0 else 0.0
        f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0

        return {
            'precision': precision,
            'recall': recall,
            'f1': f1
        }

    def _calculate_relation_metrics(
        self,
        generated_tree: Dict[str, Any],
        gold_standard_tree: Dict[str, Any]
    ) -> Dict[str, float]:
        """
        计算关系抽取指标

        参数:
            generated_tree: 生成的故障树
            gold_standard_tree: 标准故障树

        返回:
            包含 accuracy, gate_accuracy 的字典
        """
        generated_relations = self._extract_relations(generated_tree)
        gold_standard_relations = self._extract_relations(gold_standard_tree)

        total_relations = len(gold_standard_relations)
        if total_relations == 0:
            return {'accuracy': 0.0, 'gate_accuracy': 0.0}

        correct_relations = 0
        correct_gates = 0

        for gen_rel in generated_relations:
            for gold_rel in gold_standard_relations:
                if gen_rel['parent_id'] == gold_rel['parent_id'] and \
                   gen_rel['child_id'] == gold_rel['child_id']:
                    correct_relations += 1
                    if gen_rel.get('gate_type') == gold_rel.get('gate_type'):
                        correct_gates += 1
                    break

        relation_accuracy = correct_relations / total_relations
        gate_accuracy = correct_gates / total_relations if total_relations > 0 else 0.0

        return {
            'accuracy': relation_accuracy,
            'gate_accuracy': gate_accuracy
        }

    def _extract_relations(self, tree: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        提取故障树中的所有关系

        参数:
            tree: 故障树

        返回:
            关系列表
        """
        relations = []

        def traverse(node: Dict[str, Any]):
            if not node:
                return

            parent_id = node.get('event_id')
            gate_type = node.get('gate_type')

            if 'children' in node and node['children']:
                for child in node['children']:
                    relations.append({
                        'parent_id': parent_id,
                        'child_id': child.get('event_id'),
                        'gate_type': gate_type
                    })
                    traverse(child)

        traverse(tree)
        return relations

    def _calculate_overall_score(
        self,
        structure_accuracy: float,
        event_f1: float,
        relation_accuracy: float
    ) -> float:
        """
        计算综合得分

        参数:
            structure_accuracy: 结构准确率
            event_f1: 事件 F1 值
            relation_accuracy: 关系准确率

        返回:
            综合得分
        """
        score = (
            structure_accuracy * self.weights['structure'] +
            event_f1 * self.weights['event'] +
            relation_accuracy * self.weights['relation']
        )
        return round(score, 4)

    def _analyze_differences(
        self,
        generated_nodes: List[Dict[str, Any]],
        gold_standard_nodes: List[Dict[str, Any]],
        generated_tree: Dict[str, Any],
        gold_standard_tree: Dict[str, Any]
    ) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]], List[Dict[str, Any]]]:
        """
        分析差异

        参数:
            generated_nodes: 生成的节点列表
            gold_standard_nodes: 标准节点列表
            generated_tree: 生成的故障树
            gold_standard_tree: 标准故障树

        返回:
            (缺少的事件列表, 多余的事件列表, 错误的关系列表)
        """
        generated_ids = {node['event_id'] for node in generated_nodes}
        gold_ids = {node['event_id'] for node in gold_standard_nodes}

        missing_events = [
            {
                'event_id': node['event_id'],
                'event_name': node['event_name'],
                'event_type': node['event_type']
            }
            for node in gold_standard_nodes if node['event_id'] not in generated_ids
        ]

        extra_events = [
            {
                'event_id': node['event_id'],
                'event_name': node['event_name'],
                'event_type': node['event_type']
            }
            for node in generated_nodes if node['event_id'] not in gold_ids
        ]

        wrong_relations = []
        generated_relations = self._extract_relations(generated_tree)
        gold_standard_relations = self._extract_relations(gold_standard_tree)

        for gold_rel in gold_standard_relations:
            found = False
            for gen_rel in generated_relations:
                if gen_rel['parent_id'] == gold_rel['parent_id'] and \
                   gen_rel['child_id'] == gold_rel['child_id']:
                    found = True
                    if gen_rel.get('gate_type') != gold_rel.get('gate_type'):
                        wrong_relations.append({
                            'parent_id': gold_rel['parent_id'],
                            'child_id': gold_rel['child_id'],
                            'expected_gate': gold_rel.get('gate_type'),
                            'actual_gate': gen_rel.get('gate_type')
                        })
                    break
            if not found:
                wrong_relations.append({
                    'parent_id': gold_rel['parent_id'],
                    'child_id': gold_rel['child_id'],
                    'expected_gate': gold_rel.get('gate_type'),
                    'actual_gate': None,
                    'error': '关系缺失'
                })

        return missing_events, extra_events, wrong_relations

    def set_weights(
        self,
        structure_weight: float,
        event_weight: float,
        relation_weight: float
    ):
        """
        设置评估权重

        参数:
            structure_weight: 结构权重
            event_weight: 事件权重
            relation_weight: 关系权重
        """
        total = structure_weight + event_weight + relation_weight
        if abs(total - 1.0) > 0.001:
            raise ValueError("权重总和必须为 1.0")

        self.weights = {
            'structure': structure_weight,
            'event': event_weight,
            'relation': relation_weight
        }
