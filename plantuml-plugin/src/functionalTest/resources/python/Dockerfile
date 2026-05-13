FROM python:3.12-slim

RUN pip install --no-cache-dir uv

COPY requirements.txt .

RUN uv pip install --system --no-cache -r requirements.txt

WORKDIR /scripts
