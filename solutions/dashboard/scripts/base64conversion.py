import base64


def base64Encode(string: str):
    string_bytes = string.encode("ascii")
    base64_bytes = base64.b64encode(string_bytes)
    base64_string = base64_bytes.decode("ascii")
    return base64_string


def main():
    plain_text_data = {
        "mongodb_username": "<data>",
        "mongodb_password": "<data>",
        "codacy_api_token": "<data>",
        "elasticsearch_username": "<data>",
        "elasticsearch_password": "<data>",
        "logstash_password": "<data>"
    }

    encoded_data = {}

    # Looping through dictionary
    for key in plain_text_data:
        val = plain_text_data[key]
        encoded_data[key] = base64Encode(val)

    # Displaying Data
    for key in encoded_data:
        print(key, ": ", encoded_data[key])


if __name__ == "__main__":
    main()
