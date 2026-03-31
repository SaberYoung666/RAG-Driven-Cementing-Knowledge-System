"""项目运行入口，导出应用对象供 ASGI 服务器启动。"""
from app.main import app, create_app

__all__ = ["app", "create_app"]

