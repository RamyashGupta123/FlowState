FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
RUN python -m classifier.train || echo 'Training skipped — will use rules fallback'
CMD ["python", "-m", "classifier.server"]
