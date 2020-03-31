#!/bin/bash -eux

pushd zebedee
    if [[ "$APPLICATION" == "zebedee" ]]; then
        make test-cms
    elif [[ "$APPLICATION" == "zebedee-reader" ]]; then
        make test-reader
    fi
popd
