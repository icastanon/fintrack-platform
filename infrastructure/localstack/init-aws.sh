#!/bin/sh
set -eu

ensure_queue() {
    queue_name="$1"

    if ! awslocal sqs get-queue-url --queue-name "$queue_name" > /dev/null 2>&1; then
        awslocal sqs create-queue --queue-name "$queue_name" > /dev/null
    fi
}

configure_main_queue() {
    main_queue="$1"
    dead_letter_queue="$2"

    ensure_queue "$dead_letter_queue"
    ensure_queue "$main_queue"

    dlq_url=$(awslocal sqs get-queue-url \
        --queue-name "$dead_letter_queue" \
        --query QueueUrl \
        --output text)

    dlq_arn=$(awslocal sqs get-queue-attributes \
        --queue-url "$dlq_url" \
        --attribute-names QueueArn \
        --query Attributes.QueueArn \
        --output text)

    main_url=$(awslocal sqs get-queue-url \
        --queue-name "$main_queue" \
        --query QueueUrl \
        --output text)

    attributes=$(printf \
        '{"VisibilityTimeout":"30","ReceiveMessageWaitTimeSeconds":"20","RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"3\\"}"}' \
        "$dlq_arn")

    awslocal sqs set-queue-attributes \
        --queue-url "$main_url" \
        --attributes "$attributes"
}

if ! awslocal s3api head-bucket --bucket fintrack-imports > /dev/null 2>&1; then
    awslocal s3api create-bucket --bucket fintrack-imports > /dev/null
fi

configure_main_queue \
    fintrack-transaction-processing \
    fintrack-transaction-processing-dlq

configure_main_queue \
    fintrack-import-jobs \
    fintrack-import-jobs-dlq