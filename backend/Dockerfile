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
# Behind a load balancer: set FORWARDED_ALLOW_IPS to its address so rate limits see the real client IP.
ENV FORWARDED_ALLOW_IPS=127.0.0.1
# Apply migrations, then serve. No --reload, no debug; 2 workers per container by default.
CMD ["sh", "-c", ".venv/bin/alembic upgrade head && .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000 --proxy-headers --forwarded-allow-ips \"$FORWARDED_ALLOW_IPS\" --workers ${WEB_WORKERS:-2} --no-server-header"]
