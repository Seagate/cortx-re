FROM python:3.10.5-alpine3.16

COPY . /app

RUN pip install --upgrade pip
RUN pip3 install /app

ENV PYTHONUNBUFFERED=1

ENTRYPOINT ["cortxportscanner", "--namespace", "cortx", "--cr-name", "cortx-port-scanner"]
