## aws-lambda with phantomjs demo

Make a screen capture 

```
{
  "cmd": "screen-capture", "bucket":"the-bucket-name-where-screen-capture-saved", "url": "https://github.com/qrtt1/aws-lambda-phantomjs"
}
```

Run commands

```
{
  "cmd": "/bin/ls", "args":["-al", "/var/task"]
}
```

## known issues

* we have no write permissions for generating fontconfig caches (no Fonts for CJK)
