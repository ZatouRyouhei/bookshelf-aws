cd /d %~dp0
call mvn package
aws lambda update-function-code --function-name bookshelf-function --zip-file fileb://target/bookshelf-aws-1.0.jar