FROM python:3.10-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources 2>/dev/null || true \
    && sed -i 's|security.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources 2>/dev/null || true \
    && sed -i 's|deb.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list 2>/dev/null || true \
    && sed -i 's|security.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list 2>/dev/null || true \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
    build-essential \
    libglib2.0-0 \
    libgl1 \
    libgomp1 \
    libsm6 \
    libxext6 \
    libxrender1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./

ENV PIP_DEFAULT_TIMEOUT=600
ENV PIP_TIMEOUT=600

RUN python -m pip install --upgrade pip && \
    pip install --timeout 600 --retries 20 \
    --index-url https://download.pytorch.org/whl/cpu torch && \
    pip install --timeout 600 --retries 20 -r requirements.txt

COPY . .

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]