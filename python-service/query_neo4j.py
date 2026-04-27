#!/usr/bin/env python
"""
Neo4j 知识图谱查询工具
功能：
1. 连接并查询知识图谱
2. 查看节点和关系
3. 删除指定数据
"""

from neo4j import GraphDatabase, exceptions
import json

# 配置（与项目一致）
URI = "bolt://localhost:7687"
USERNAME = "neo4j"
PASSWORD = "wry5054755"

def connect_neo4j():
    """连接 Neo4j 数据库"""
    print(f"正在连接 Neo4j: {URI}")
    try:
        driver = GraphDatabase.driver(URI, auth=(USERNAME, PASSWORD))
        driver.verify_connectivity()
        print("✓ 成功连接 Neo4j 数据库")
        return driver
    except Exception as e:
        print(f"✗ 连接失败: {e}")
        return None

def execute_query(driver, query, parameters=None):
    """执行 Cypher 查询"""
    try:
        with driver.session() as session:
            result = session.run(query, parameters or {})
            return list(result)
    except Exception as e:
        print(f"✗ 查询失败: {e}")
        return None

def show_stats(driver):
    """显示数据库统计"""
    print("\n" + "="*60)
    print("📊 知识图谱统计")
    print("="*60)
    
    # 节点统计
    node_counts = execute_query(driver, "MATCH (n) RETURN labels(n) as label, count(n) as count")
    if node_counts:
        print("\n节点统计:")
        for record in node_counts:
            print(f"  {record['label']}: {record['count']}")
    
    # 关系统计
    rel_counts = execute_query(driver, "MATCH ()-[r]->() RETURN type(r) as type, count(r) as count")
    if rel_counts:
        print("\n关系统计:")
        for record in rel_counts:
            print(f"  {record['type']}: {record['count']}")
    
    # 用户统计
    user_stats = execute_query(driver, "MATCH (e:UserEvent) RETURN e.userId as userId, count(e) as count")
    if user_stats:
        print("\n用户数据统计:")
        for record in user_stats:
            print(f"  {record['userId']}: {record['count']} 个节点")

def show_all_user_events(driver, user_id=None, limit=50):
    """显示 UserEvent 节点"""
    if user_id:
        query = """
        MATCH (e:UserEvent {userId: $userId})
        RETURN e
        LIMIT $limit
        """
        params = {"userId": user_id, "limit": limit}
        print(f"\n用户 {user_id} 的 UserEvent 节点（前{limit}个）:")
    else:
        query = """
        MATCH (e:UserEvent)
        RETURN e
        LIMIT $limit
        """
        params = {"limit": limit}
        print(f"\n所有 UserEvent 节点（前{limit}个）:")
    
    results = execute_query(driver, query, params)
    if results:
        for i, record in enumerate(results, 1):
            node = record['e']
            print(f"\n{i}. {node.get('eventType', 'N/A')}")
            print(f"   ID: {node.get('docId', 'N/A')}")
            print(f"   User: {node.get('userId', 'N/A')}")
            print(f"   Description: {node.get('description', 'N/A')[:100] if node.get('description') else 'N/A'}...")

def delete_user_document_events(driver, user_id, doc_id):
    """删除指定用户和文档的知识图谱数据"""
    confirm = input(f"\n⚠️  确定要删除用户 {user_id} 的文档 {doc_id} 的知识图谱数据吗？(yes/no): ")
    if confirm.lower() != 'yes':
        print("已取消")
        return
    
    query = """
    MATCH (e:UserEvent)
    WHERE e.userId = $userId AND e.docId = $docId
    DETACH DELETE e
    """
    
    try:
        execute_query(driver, query, {"userId": user_id, "docId": doc_id})
        print("✓ 成功删除知识图谱数据")
    except Exception as e:
        print(f"✗ 删除失败: {e}")

def delete_all_user_events(driver, user_id):
    """删除指定用户的所有知识图谱数据"""
    confirm = input(f"\n⚠️  确定要删除用户 {user_id} 的所有知识图谱数据吗？(yes/no): ")
    if confirm.lower() != 'yes':
        print("已取消")
        return
    
    query = """
    MATCH (e:UserEvent)
    WHERE e.userId = $userId
    DETACH DELETE e
    """
    
    try:
        execute_query(driver, query, {"userId": user_id})
        print("✓ 成功删除用户知识图谱数据")
    except Exception as e:
        print(f"✗ 删除失败: {e}")

def delete_all_data(driver):
    """删除所有知识图谱数据"""
    confirm = input("\n⚠️  ⚠️  ⚠️  确定要删除所有知识图谱数据吗？(YES/no): ")
    if confirm != 'YES':
        print("已取消")
        return
    
    try:
        execute_query(driver, "MATCH (n) DETACH DELETE n")
        print("✓✓✓ 成功删除所有知识图谱数据！✓✓✓")
    except Exception as e:
        print(f"✗ 删除失败: {e}")

def show_menu():
    """显示菜单"""
    print("\n" + "="*60)
    print("🎯 Neo4j 知识图谱查询工具")
    print("="*60)
    print("1. 查看数据库统计信息")
    print("2. 查看所有 UserEvent 节点")
    print("3. 查看指定用户的 UserEvent 节点")
    print("4. 删除指定用户的指定文档的数据")
    print("5. 删除指定用户的所有数据")
    print("6. 删除所有知识图谱数据")
    print("0. 退出")
    print("="*60)

def main():
    """主函数"""
    driver = connect_neo4j()
    if not driver:
        return
    
    try:
        while True:
            show_menu()
            choice = input("\n请选择操作 (0-6): ").strip()
            
            if choice == '0':
                print("👋 再见！")
                break
            elif choice == '1':
                show_stats(driver)
            elif choice == '2':
                limit = input("显示数量限制 (默认 50): ").strip()
                limit = int(limit) if limit.isdigit() else 50
                show_all_user_events(driver, limit=limit)
            elif choice == '3':
                user_id = input("请输入用户ID: ").strip()
                if user_id:
                    limit = input("显示数量限制 (默认 50): ").strip()
                    limit = int(limit) if limit.isdigit() else 50
                    show_all_user_events(driver, user_id=user_id, limit=limit)
            elif choice == '4':
                user_id = input("请输入用户ID: ").strip()
                doc_id = input("请输入文档ID: ").strip()
                if user_id and doc_id:
                    delete_user_document_events(driver, user_id, doc_id)
            elif choice == '5':
                user_id = input("请输入用户ID: ").strip()
                if user_id:
                    delete_all_user_events(driver, user_id)
            elif choice == '6':
                delete_all_data(driver)
            else:
                print("✗ 无效选择，请重试")
    finally:
        driver.close()
        print("连接已关闭")

if __name__ == "__main__":
    main()
