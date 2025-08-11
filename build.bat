cd /d %~dp0
call mvn package
aws s3 rm s3://satouryouhei-sample-bucket/bookshelf-aws-1.0.jar
aws s3 cp target/bookshelf-aws-1.0.jar s3://satouryouhei-sample-bucket
aws lambda update-function-code --function-name bookshelf-function --s3-bucket satouryouhei-sample-bucket --s3-key bookshelf-aws-1.0.jar
rem aws lambda update-function-code --function-name bookshelf-function --zip-file fileb://target/bookshelf-aws-1.0.jar