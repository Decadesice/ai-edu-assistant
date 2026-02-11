FROM python:3.11-slim

ENV PIP_NO_CACHE_DIR=1

RUN pip install -U pip && pip install chromadb==1.4.1

WORKDIR /chroma
EXPOSE 8000

CMD ["chroma","run","--host","0.0.0.0","--port","8000","--path","/chroma/data"]
