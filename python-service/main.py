import os
import sys
import multiprocessing
from typing import List, Tuple

def start_rag_service(port: int):
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'rag_generation_service'))
    os.chdir(os.path.join(os.path.dirname(__file__), 'rag_generation_service'))

    from dotenv import load_dotenv
    load_dotenv()

    import uvicorn
    from rag_generation_service.rag_api import app

    print(f"[RAG Service] Starting on port {port}...")
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")

def start_chat_service(port: int):
    service_dir = os.path.join(os.path.dirname(__file__), 'chat_service')
    sys.path.insert(0, service_dir)
    os.chdir(service_dir)

    from dotenv import load_dotenv
    load_dotenv()

    import uvicorn
    from chat_service.chat_api import app

    print(f"[Chat Service] Starting on port {port}...")
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")

def start_fusion_service(port: int):
    service_dir = os.path.join(os.path.dirname(__file__), 'fusion_service')
    sys.path.insert(0, service_dir)
    sys.path.insert(0, os.path.dirname(__file__))
    os.chdir(service_dir)

    from dotenv import load_dotenv
    load_dotenv()

    import uvicorn
    from fusion_service.fusion_api import app

    print(f"[Fusion Service] Starting on port {port}...")
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")

def start_evaluation_service(port: int):
    service_dir = os.path.join(os.path.dirname(__file__), 'evaluation_service')
    sys.path.insert(0, service_dir)
    sys.path.insert(0, os.path.dirname(__file__))
    os.chdir(service_dir)

    from dotenv import load_dotenv
    load_dotenv()

    import uvicorn
    from evaluation_service.evaluation_api import app

    print(f"[Evaluation Service] Starting on port {port}...")
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")

def start_classification_service(port: int):
    service_dir = os.path.join(os.path.dirname(__file__), 'classification_service')
    sys.path.insert(0, service_dir)
    sys.path.insert(0, os.path.dirname(__file__))
    os.chdir(service_dir)

    from dotenv import load_dotenv
    load_dotenv()

    import uvicorn
    from classification_service.classification_api import app

    print(f"[Classification Service] Starting on port {port}...")
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")

def main():
    print("=" * 70)
    print("故障树分析系统 - Python 服务集群启动")
    print("=" * 70)

    services: List[Tuple[str, int, callable]] = [
        ("RAG Generation Service", 8000, start_rag_service),
        ("Chat Service", 8001, start_chat_service),
        ("Fusion Service", 8002, start_fusion_service),
        ("Evaluation Service", 8003, start_evaluation_service),
        ("Classification Service", 8004, start_classification_service),
    ]

    processes: List[multiprocessing.Process] = []

    for name, port, start_func in services:
        try:
            p = multiprocessing.Process(target=start_func, args=(port,), name=name)
            p.daemon = True
            p.start()
            processes.append(p)
            print(f"[Launched] {name} on port {port} (PID: {p.pid})")
        except Exception as e:
            print(f"[Error] Failed to start {name}: {e}")

    print("\n" + "=" * 70)
    print("All Python Services Started!")
    print("=" * 70)
    print("\nService Endpoints:")
    for name, port, _ in services:
        print(f"  {name}: http://localhost:{port}")

    print("\nPress Ctrl+C to stop all services...")

    try:
        for p in processes:
            p.join()
    except KeyboardInterrupt:
        print("\n[Shutdown] Stopping all services...")
        for p in processes:
            if p.is_alive():
                p.terminate()
        print("[Shutdown] All services stopped.")

if __name__ == "__main__":
    multiprocessing.set_start_method('spawn', force=True)
    main()
