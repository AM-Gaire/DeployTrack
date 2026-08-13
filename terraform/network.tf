# Networking.
#
# Deliberately no NAT gateway. A NAT would let the private subnet reach the
# internet, and costs roughly £28 a month whether it carries traffic or not --
# it is the single most common source of surprise AWS bills. Nothing here needs
# it: RDS never makes outbound calls, and the EC2 instance sits in a public
# subnet reaching ECR and SSM through the internet gateway.

resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  # RDS hands out a DNS hostname rather than an IP, so without this the
  # application cannot resolve its own database.
  enable_dns_hostnames = true

  tags = { Name = "${var.project_name}-vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.project_name}-igw" }
}

# Picks real availability zones for whichever region is configured, rather than
# hardcoding names that only exist in one region.
data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = { Name = "${var.project_name}-public" }
}

# Two private subnets in different availability zones. Only one is used, but
# RDS refuses to create a subnet group with fewer than two AZs -- it wants the
# option of failing over even when you have not asked for it.
resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.${count.index + 10}.0/24"
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = { Name = "${var.project_name}-private-${count.index}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${var.project_name}-public-rt" }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# The private subnets get no route to the internet gateway at all. That is the
# point: the database cannot be reached from outside the VPC, and cannot reach
# out either.

resource "aws_security_group" "app" {
  name        = "${var.project_name}-app"
  description = "Application instance"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP from the internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = [var.allowed_http_cidr]
  }

  # Only created when ssh_ingress_cidr is set. Leaving SSH open to 0.0.0.0/0 is
  # how instances get found and brute-forced within hours of launching.
  dynamic "ingress" {
    for_each = var.ssh_ingress_cidr == "" ? [] : [var.ssh_ingress_cidr]
    content {
      description = "SSH from a specific address"
      from_port   = 22
      to_port     = 22
      protocol    = "tcp"
      cidr_blocks = [ingress.value]
    }
  }

  egress {
    description = "Outbound for pulling images and reading parameters"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-app-sg" }
}

resource "aws_security_group" "database" {
  name        = "${var.project_name}-db"
  description = "PostgreSQL, reachable only from the application"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "PostgreSQL from the application instance only"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    # Referencing the other security group rather than a CIDR means the rule
    # keeps working if the instance is replaced and its address changes, and
    # nothing outside that group can connect even from inside the VPC.
    security_groups = [aws_security_group.app.id]
  }

  tags = { Name = "${var.project_name}-db-sg" }
}
