data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "fintrack" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${local.resource_prefix}-vpc"
  }
}

resource "aws_internet_gateway" "fintrack" {
  vpc_id = aws_vpc.fintrack.id

  tags = {
    Name = "${local.resource_prefix}-igw"
  }
}

resource "aws_subnet" "public" {
  for_each = local.public_subnets

  vpc_id                  = aws_vpc.fintrack.id
  availability_zone       = each.key
  cidr_block              = each.value
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.resource_prefix}-public-${each.key}"
    Tier = "public"
  }
}

resource "aws_subnet" "private_application" {
  for_each = local.private_application_subnets

  vpc_id                  = aws_vpc.fintrack.id
  availability_zone       = each.key
  cidr_block              = each.value
  map_public_ip_on_launch = false

  tags = {
    Name = "${local.resource_prefix}-private-application-${each.key}"
    Tier = "private-application"
  }
}

resource "aws_subnet" "private_data" {
  for_each = local.private_data_subnets

  vpc_id                  = aws_vpc.fintrack.id
  availability_zone       = each.key
  cidr_block              = each.value
  map_public_ip_on_launch = false

  tags = {
    Name = "${local.resource_prefix}-private-data-${each.key}"
    Tier = "private-data"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.fintrack.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.fintrack.id
  }

  tags = {
    Name = "${local.resource_prefix}-public-route-table"
  }
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}