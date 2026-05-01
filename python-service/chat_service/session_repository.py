import os
import json
from typing import List, Dict, Any, Optional
from datetime import datetime
from dotenv import load_dotenv
from db_utils import get_db_connection, generate_id, parse_json_list, to_json_list

load_dotenv()

class ChatSessionRepository:
    def create_session(self, user_id: str, title: Optional[str] = None,
                       linked_tree_ids: Optional[List[str]] = None,
                       linked_doc_ids: Optional[List[str]] = None) -> Dict[str, Any]:
        session_id = f"session_{generate_id()}"
        now = datetime.now()

        try:
            with get_db_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("""
                    INSERT INTO ai_chat_session
                    (session_id, user_id, title, created_at, updated_at,
                     linked_tree_ids, linked_doc_ids, status)
                    VALUES (:1, :2, :3, :4, :5, :6, :7, 'ACTIVE')
                """, [
                    session_id,
                    user_id,
                    title or f"对话 {now.strftime('%Y-%m-%d %H:%M')}",
                    now,
                    now,
                    to_json_list(linked_tree_ids or []),
                    to_json_list(linked_doc_ids or [])
                ])
        except Exception as e:
            import traceback
            print(f"Database INSERT error: {e}")
            traceback.print_exc()
            raise e

        return self.get_session(session_id)

    def get_session(self, session_id: str) -> Optional[Dict[str, Any]]:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT session_id, user_id, title, created_at, updated_at,
                       linked_tree_ids, linked_doc_ids, status
                FROM ai_chat_session
                WHERE session_id = :1
            """, [session_id])

            row = cursor.fetchone()
            if not row:
                return None

            cursor.execute("""
                SELECT message_id, session_id, role, content, timestamp, metadata
                FROM ai_chat_message
                WHERE session_id = :1
                ORDER BY timestamp ASC
            """, [session_id])

            message_rows = cursor.fetchall()
            messages = [
                {
                    'messageId': mr[0],
                    'sessionId': mr[1],
                    'role': mr[2],
                    'content': mr[3].read() if hasattr(mr[3], 'read') else mr[3],
                    'timestamp': mr[4].isoformat() if mr[4] else None,
                    'metadata': parse_json_list(mr[5]) if mr[5] else None
                }
                for mr in message_rows
            ]

            return {
                'sessionId': row[0],
                'userId': row[1],
                'title': row[2],
                'createdAt': row[3].isoformat() if row[3] else None,
                'updatedAt': row[4].isoformat() if row[4] else None,
                'linkedTreeIds': parse_json_list(row[5]),
                'linkedDocIds': parse_json_list(row[6]),
                'status': row[7],
                'messages': messages
            }

    def get_user_sessions(self, user_id: str) -> List[Dict[str, Any]]:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT session_id, user_id, title, created_at, updated_at,
                       linked_tree_ids, linked_doc_ids, status
                FROM ai_chat_session
                WHERE user_id = :1
                ORDER BY updated_at DESC
            """, [user_id])

            rows = cursor.fetchall()
            return [
                {
                    'sessionId': row[0],
                    'userId': row[1],
                    'title': row[2],
                    'createdAt': row[3].isoformat() if row[3] else None,
                    'updatedAt': row[4].isoformat() if row[4] else None,
                    'linkedTreeIds': parse_json_list(row[5]),
                    'linkedDocIds': parse_json_list(row[6]),
                    'status': row[7]
                }
                for row in rows
            ]

    def update_session(self, session_id: str, **kwargs) -> Optional[Dict[str, Any]]:
        updates = []
        params = []

        if 'title' in kwargs:
            updates.append("title = :1")
            params.append(kwargs['title'])
        if 'linked_tree_ids' in kwargs:
            updates.append("linked_tree_ids = :{}".format(len(params) + 1))
            params.append(to_json_list(kwargs['linked_tree_ids']))
        if 'linked_doc_ids' in kwargs:
            updates.append("linked_doc_ids = :{}".format(len(params) + 1))
            params.append(to_json_list(kwargs['linked_doc_ids']))
        if 'status' in kwargs:
            updates.append("status = :{}".format(len(params) + 1))
            params.append(kwargs['status'])

        if updates:
            updates.append("updated_at = :{}".format(len(params) + 1))
            params.append(datetime.now())
            params.append(session_id)

            with get_db_connection() as conn:
                cursor = conn.cursor()
                sql = f"UPDATE ai_chat_session SET {', '.join(updates)} WHERE session_id = :{len(params)}"
                cursor.execute(sql, params)

        return self.get_session(session_id)

    def delete_session(self, session_id: str) -> bool:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM ai_chat_session WHERE session_id = :1", [session_id])
            return cursor.rowcount > 0

    def add_message(self, session_id: str, role: str, content: str,
                    metadata: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        message_id = f"msg_{generate_id()}"
        now = datetime.now()

        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO ai_chat_message
                (message_id, session_id, role, content, timestamp, metadata)
                VALUES (:1, :2, :3, :4, :5, :6)
            """, [
                message_id,
                session_id,
                role,
                content,
                now,
                json.dumps(metadata, ensure_ascii=False) if metadata else None
            ])

            cursor.execute("""
                UPDATE ai_chat_session SET updated_at = :1 WHERE session_id = :2
            """, [now, session_id])

        return {
            'messageId': message_id,
            'sessionId': session_id,
            'role': role,
            'content': content,
            'timestamp': now.isoformat(),
            'metadata': metadata
        }

    def get_session_messages(self, session_id: str) -> List[Dict[str, Any]]:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT message_id, session_id, role, content, timestamp, metadata
                FROM ai_chat_message
                WHERE session_id = :1
                ORDER BY timestamp ASC
            """, [session_id])

            rows = cursor.fetchall()
            return [
                {
                    'messageId': row[0],
                    'sessionId': row[1],
                    'role': row[2],
                    'content': row[3].read() if hasattr(row[3], 'read') else row[3],
                    'timestamp': row[4].isoformat() if row[4] else None,
                    'metadata': parse_json_list(row[5]) if row[5] else None
                }
                for row in rows
            ]

    def link_tree(self, session_id: str, tree_id: str) -> Optional[List[str]]:
        session = self.get_session(session_id)
        if not session:
            return None

        linked_trees = session['linkedTreeIds']
        if tree_id not in linked_trees:
            linked_trees.append(tree_id)
            self.update_session(session_id, linked_tree_ids=linked_trees)

        return linked_trees

    def unlink_tree(self, session_id: str, tree_id: str) -> Optional[List[str]]:
        session = self.get_session(session_id)
        if not session:
            return None

        linked_trees = session['linkedTreeIds']
        if tree_id in linked_trees:
            linked_trees.remove(tree_id)
            self.update_session(session_id, linked_tree_ids=linked_trees)

        return linked_trees

    def link_document(self, session_id: str, doc_id: str) -> Optional[List[str]]:
        session = self.get_session(session_id)
        if not session:
            return None

        linked_docs = session['linkedDocIds']
        if doc_id not in linked_docs:
            linked_docs.append(doc_id)
            self.update_session(session_id, linked_doc_ids=linked_docs)

        return linked_docs

    def unlink_document(self, session_id: str, doc_id: str) -> Optional[List[str]]:
        session = self.get_session(session_id)
        if not session:
            return None

        linked_docs = session['linkedDocIds']
        if doc_id in linked_docs:
            linked_docs.remove(doc_id)
            self.update_session(session_id, linked_doc_ids=linked_docs)

        return linked_docs
