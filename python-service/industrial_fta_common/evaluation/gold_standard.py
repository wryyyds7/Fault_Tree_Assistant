# Gold Standard Manager
# 管理专家构建的标准故障树（黄金标准集）

import json
import os
from typing import List, Dict, Any, Optional
from dataclasses import dataclass
from datetime import datetime

@dataclass
class GoldStandardFaultTree:
    """黄金标准故障树"""
    tree_id: str
    name: str
    equipment_type: str
    fault_mode: str
    difficulty_level: str
    tree_data: Dict[str, Any]
    created_at: datetime
    updated_at: datetime
    metadata: Dict[str, Any]

class GoldStandardManager:
    """黄金标准集管理器"""

    def __init__(self, storage_path: Optional[str] = None):
        """
        初始化黄金标准集管理器

        参数:
            storage_path: 存储路径，默认为 None（使用内存存储）
        """
        self.storage_path = storage_path or self._get_default_path()
        self._gold_standards: Dict[str, GoldStandardFaultTree] = {}
        self._load_gold_standards()

    def _get_default_path(self) -> str:
        """获取默认存储路径"""
        current_dir = os.path.dirname(os.path.abspath(__file__))
        return os.path.join(current_dir, '..', '..', 'data', 'gold_standards')

    def _load_gold_standards(self):
        """从存储路径加载黄金标准集"""
        if not os.path.exists(self.storage_path):
            os.makedirs(self.storage_path, exist_ok=True)
            return

        for filename in os.listdir(self.storage_path):
            if filename.endswith('.json'):
                filepath = os.path.join(self.storage_path, filename)
                try:
                    with open(filepath, 'r', encoding='utf-8') as f:
                        data = json.load(f)
                        tree = self._deserialize_gold_standard(data)
                        self._gold_standards[tree.tree_id] = tree
                except Exception as e:
                    print(f"Error loading gold standard {filename}: {e}")

    def _serialize_gold_standard(self, tree: GoldStandardFaultTree) -> Dict[str, Any]:
        """序列化黄金标准树"""
        return {
            'tree_id': tree.tree_id,
            'name': tree.name,
            'equipment_type': tree.equipment_type,
            'fault_mode': tree.fault_mode,
            'difficulty_level': tree.difficulty_level,
            'tree_data': tree.tree_data,
            'created_at': tree.created_at.isoformat() if tree.created_at else None,
            'updated_at': tree.updated_at.isoformat() if tree.updated_at else None,
            'metadata': tree.metadata
        }

    def _deserialize_gold_standard(self, data: Dict[str, Any]) -> GoldStandardFaultTree:
        """反序列化黄金标准树"""
        return GoldStandardFaultTree(
            tree_id=data['tree_id'],
            name=data['name'],
            equipment_type=data['equipment_type'],
            fault_mode=data['fault_mode'],
            difficulty_level=data['difficulty_level'],
            tree_data=data['tree_data'],
            created_at=datetime.fromisoformat(data['created_at']) if data.get('created_at') else datetime.now(),
            updated_at=datetime.fromisoformat(data['updated_at']) if data.get('updated_at') else datetime.now(),
            metadata=data.get('metadata', {})
        )

    def add_gold_standard(self, tree: GoldStandardFaultTree) -> bool:
        """
        添加黄金标准故障树

        参数:
            tree: 黄金标准故障树

        返回:
            是否添加成功
        """
        try:
            self._gold_standards[tree.tree_id] = tree

            if self.storage_path:
                filepath = os.path.join(self.storage_path, f"{tree.tree_id}.json")
                with open(filepath, 'w', encoding='utf-8') as f:
                    json.dump(self._serialize_gold_standard(tree), f, ensure_ascii=False, indent=2)

            return True
        except Exception as e:
            print(f"Error adding gold standard: {e}")
            return False

    def get_gold_standard(self, tree_id: str) -> Optional[GoldStandardFaultTree]:
        """
        获取指定 ID 的黄金标准故障树

        参数:
            tree_id: 故障树 ID

        返回:
            黄金标准故障树，如果不存在则返回 None
        """
        return self._gold_standards.get(tree_id)

    def list_gold_standards(
        self,
        equipment_type: Optional[str] = None,
        fault_mode: Optional[str] = None,
        difficulty_level: Optional[str] = None
    ) -> List[GoldStandardFaultTree]:
        """
        列出黄金标准故障树

        参数:
            equipment_type: 设备类型过滤
            fault_mode: 故障模式过滤
            difficulty_level: 难度等级过滤

        返回:
            符合条件的黄金标准故障树列表
        """
        results = list(self._gold_standards.values())

        if equipment_type:
            results = [t for t in results if t.equipment_type == equipment_type]
        if fault_mode:
            results = [t for t in results if t.fault_mode == fault_mode]
        if difficulty_level:
            results = [t for t in results if t.difficulty_level == difficulty_level]

        return results

    def delete_gold_standard(self, tree_id: str) -> bool:
        """
        删除黄金标准故障树

        参数:
            tree_id: 故障树 ID

        返回:
            是否删除成功
        """
        if tree_id not in self._gold_standards:
            return False

        try:
            del self._gold_standards[tree_id]

            if self.storage_path:
                filepath = os.path.join(self.storage_path, f"{tree_id}.json")
                if os.path.exists(filepath):
                    os.remove(filepath)

            return True
        except Exception as e:
            print(f"Error deleting gold standard: {e}")
            return False

    def count(self) -> int:
        """获取黄金标准集数量"""
        return len(self._gold_standards)

    def get_statistics(self) -> Dict[str, Any]:
        """获取黄金标准集统计信息"""
        stats = {
            'total_count': len(self._gold_standards),
            'by_equipment_type': {},
            'by_difficulty_level': {},
            'by_fault_mode': {}
        }

        for tree in self._gold_standards.values():
            stats['by_equipment_type'][tree.equipment_type] = \
                stats['by_equipment_type'].get(tree.equipment_type, 0) + 1
            stats['by_difficulty_level'][tree.difficulty_level] = \
                stats['by_difficulty_level'].get(tree.difficulty_level, 0) + 1
            stats['by_fault_mode'][tree.fault_mode] = \
                stats['by_fault_mode'].get(tree.fault_mode, 0) + 1

        return stats
