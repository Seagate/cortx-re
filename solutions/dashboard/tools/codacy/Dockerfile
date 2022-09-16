FROM python:latest

COPY . /app

WORKDIR /app

RUN pip3 install pymongo
RUN pip3 install requests
RUN pip3 install kubernetes

ENV PYTHONUNBUFFERED=1

CMD ["python3", "main.py"]