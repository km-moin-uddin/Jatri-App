FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app

RUN pip install --no-cache-dir uv
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

COPY app ./app
COPY alembic ./alembic
COPY alembic.ini ./

RUN useradd --create-home jatri
USER jatri
EXPOSE 8000
# Apply migrations, then serve.
CMD ["sh", "-c", ".venv/bin/alembic upgrade head && .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000"]
