data "aws_availability_zones" "ocr" {
  state = "available"
}

resource "aws_vpc" "ocr" {
  cidr_block           = "10.50.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags                 = { Name = "${local.name}-ocr" }
}

resource "aws_internet_gateway" "ocr" {
  vpc_id = aws_vpc.ocr.id
}

resource "aws_subnet" "ocr" {
  count                   = 2
  vpc_id                  = aws_vpc.ocr.id
  cidr_block              = cidrsubnet(aws_vpc.ocr.cidr_block, 8, count.index)
  availability_zone       = data.aws_availability_zones.ocr.names[count.index]
  map_public_ip_on_launch = true
  tags                    = { Name = "${local.name}-ocr-${count.index}" }
}

resource "aws_route_table" "ocr" {
  vpc_id = aws_vpc.ocr.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.ocr.id
  }
}

resource "aws_route_table_association" "ocr" {
  count          = 2
  subnet_id      = aws_subnet.ocr[count.index].id
  route_table_id = aws_route_table.ocr.id
}

resource "aws_security_group" "ocr" {
  name   = "${local.name}-ocr"
  vpc_id = aws_vpc.ocr.id
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_ecr_repository" "ocr" {
  name         = "${local.name}-ocr"
  force_delete = true
}

resource "aws_cloudwatch_log_group" "ocr" {
  name              = "/ecs/${local.name}-ocr"
  retention_in_days = 14
}

data "aws_iam_policy_document" "ocr_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ocr_execution" {
  name               = "${local.name}-ocr-exec"
  assume_role_policy = data.aws_iam_policy_document.ocr_assume.json
}

resource "aws_iam_role_policy_attachment" "ocr_execution" {
  role       = aws_iam_role.ocr_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "ocr_task" {
  name               = "${local.name}-ocr-task"
  assume_role_policy = data.aws_iam_policy_document.ocr_assume.json
}

data "aws_iam_policy_document" "ocr_task" {
  statement {
    actions   = ["s3:GetObject", "s3:PutObject"]
    resources = ["${aws_s3_bucket.docs.arn}/*"]
  }
  statement {
    actions   = ["textract:DetectDocumentText"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "ocr_task" {
  name   = "${local.name}-ocr-task"
  role   = aws_iam_role.ocr_task.id
  policy = data.aws_iam_policy_document.ocr_task.json
}

resource "aws_ecs_cluster" "ocr" {
  name = "${local.name}-ocr"
}

resource "aws_ecs_task_definition" "ocr" {
  family                   = "${local.name}-ocr"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "1024"
  memory                   = "2048"
  execution_role_arn       = aws_iam_role.ocr_execution.arn
  task_role_arn            = aws_iam_role.ocr_task.arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }
  container_definitions = jsonencode([{
    name      = "ocr"
    image     = var.ocr_image != "" ? var.ocr_image : "${aws_ecr_repository.ocr.repository_url}:0.1.0"
    essential = true
    environment = [
      { name = "AWS_REGION", value = var.aws_region }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.ocr.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "ocr"
      }
    }
  }])
}
