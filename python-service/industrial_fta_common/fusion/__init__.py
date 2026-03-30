# Multi-Document Fusion Module
# 多文档融合处理模块

from .document_metadata import DocumentMetadata, SourceType
from .paragraph_cluster import ParagraphClusterer
from .conflict_detector import ConflictDetector, ConflictType
from .fusion_engine import FusionEngine
from .document_classifier import DocumentClassifier, ClassificationResult, classify_document

__all__ = [
    'DocumentMetadata',
    'SourceType',
    'ParagraphClusterer',
    'ConflictDetector',
    'ConflictType',
    'FusionEngine',
    'DocumentClassifier',
    'ClassificationResult',
    'classify_document',
]
