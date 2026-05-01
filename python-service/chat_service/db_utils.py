import os
import json
import uuid
import cx_Oracle
from typing import List, Dict, Any, Optional
from contextlib import contextmanager
from dotenv import load_dotenv

load_dotenv()

class DatabaseConfig:
    def __init__(self):
        self.host = os.getenv('DB_HOST', 'localhost')
        self.port = int(os.getenv('DB_PORT', '1521'))
        self.service = os.getenv('DB_SERVICE', 'XEPDB1')
        self.user = os.getenv('DB_USER', 'fta_user')
        self.password = os.getenv('DB_PASSWORD', '123456')
        self.encoding = os.getenv('DB_ENCODING', 'UTF-8')

    def get_connection_string(self) -> str:
        return f"{self.user}/{self.password}@{self.host}:{self.port}/{self.service}"

    def get_dsn(self) -> str:
        return cx_Oracle.makedsn(self.host, self.port, service_name=self.service)

class DatabaseConnection:
    def __init__(self, config: Optional[DatabaseConfig] = None):
        self.config = config or DatabaseConfig()
        self._connection = None

    def connect(self):
        if self._connection is None:
            self._connection = cx_Oracle.connect(
                user=self.config.user,
                password=self.config.password,
                dsn=self.config.get_dsn(),
                encoding=self.config.encoding
            )
        return self._connection

    def close(self):
        if self._connection:
            self._connection.close()
            self._connection = None

    def __enter__(self):
        return self.connect()

    def __exit__(self, exc_type, exc_val, exc_tb):
        if exc_type:
            self._connection.rollback()
        else:
            self._connection.commit()
        return False

@contextmanager
def get_db_connection():
    conn = DatabaseConnection()
    try:
        yield conn.connect()
        conn._connection.commit()
    except Exception:
        conn._connection.rollback()
        raise
    finally:
        conn.close()

def generate_id() -> str:
    return uuid.uuid4().hex

def parse_json_list(json_str: Optional[str]) -> List[str]:
    if not json_str:
        return []
    try:
        return json.loads(json_str)
    except:
        return []

def to_json_list(data: List[str]) -> str:
    return json.dumps(data, ensure_ascii=False) if data else '[]'
