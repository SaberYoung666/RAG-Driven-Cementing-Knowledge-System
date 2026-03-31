"""应用包导出入口，统一暴露 FastAPI 应用实例与工厂函数。"""
from .main import app, create_app

__all__ = ["app", "create_app"]

