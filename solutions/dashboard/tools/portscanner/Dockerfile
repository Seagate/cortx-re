FROM python:3.10.5-alpine3.16

COPY . /app

WORKDIR /app

RUN pip install --upgrade pip==22.2.2
RUN pip3 install -e .

ENV PYTHONUNBUFFERED=1

ENTRYPOINT ["dashboardportscanner", "--namespace", "dashboard", "--cr-name", "dashboard-port-scanner"]
