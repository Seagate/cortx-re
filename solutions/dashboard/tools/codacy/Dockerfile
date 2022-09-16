FROM python:3.10.5

COPY . /app

WORKDIR /app

RUN pip3 install pymongo==4.2.0
RUN pip3 install requests==2.28.1

ENV PYTHONUNBUFFERED=1

CMD ["python3", "main.py"]