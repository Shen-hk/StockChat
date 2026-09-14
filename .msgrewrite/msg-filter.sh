#!/bin/sh
f="$(dirname "$0")/msgs/$GIT_COMMIT.txt"
if [ -f "$f" ]; then
  cat "$f"
else
  cat
fi
