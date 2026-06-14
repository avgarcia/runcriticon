# Red del esqueleto (ADR-0006 D11, D12): VPC propia con subnets privadas para RDS,
# subnets públicas solo para el NAT Gateway, y los security groups que aíslan RDS detrás
# del VPC connector de App Runner. RDS nunca es accesible desde Internet.

locals {
  # default_tags del provider ya aporta Project/Environment/ManagedBy/CostCenter (ADR-0006 D25).
  # Aquí se añade el tag Module obligatorio por recurso.
  module_tags = { Module = "red" }
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}" })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}-igw" })
}

# --- Subnets ---
# Privadas: RDS y recursos sensibles, sin ruta directa a Internet (ADR-0006 D11).
resource "aws_subnet" "private" {
  count             = length(var.azs)
  vpc_id            = aws_vpc.this.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone = var.azs[count.index]

  tags = merge(local.module_tags, {
    Name = "runcriticon-${var.environment}-private-${var.azs[count.index]}"
    Tier = "private"
  })
}

# Públicas: solo para el NAT Gateway y endpoints externos cuando apliquen (ADR-0006 D11).
resource "aws_subnet" "public" {
  count                   = length(var.azs)
  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index + 100)
  availability_zone       = var.azs[count.index]
  map_public_ip_on_launch = false

  tags = merge(local.module_tags, {
    Name = "runcriticon-${var.environment}-public-${var.azs[count.index]}"
    Tier = "public"
  })
}

# --- NAT Gateway (uno solo en MVP, ADR-0006 D11) ---
resource "aws_eip" "nat" {
  domain = "vpc"

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}-nat-eip" })
}

resource "aws_nat_gateway" "this" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}-nat" })

  depends_on = [aws_internet_gateway.this]
}

# --- Routing ---
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}-public-rt" })
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.this.id
  }

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}-private-rt" })
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# --- Security groups ---
# SG del VPC connector de App Runner (ADR-0006 D12). La app sale por aquí hacia RDS e Internet.
resource "aws_security_group" "connector" {
  name        = "runcriticon-${var.environment}-connector"
  description = "VPC connector de App Runner: salida de la app hacia RDS e Internet"
  vpc_id      = aws_vpc.this.id

  egress {
    description = "Salida sin restriccion (RDS via SG dedicado + Internet via NAT)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}-connector" })
}

# SG de RDS: solo acepta PostgreSQL desde el SG del connector (ADR-0006 D12). Sin acceso público.
resource "aws_security_group" "database" {
  name        = "runcriticon-${var.environment}-database"
  description = "RDS PostgreSQL: solo acepta trafico del VPC connector de App Runner"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "PostgreSQL desde el VPC connector"
    from_port       = var.db_port
    to_port         = var.db_port
    protocol        = "tcp"
    security_groups = [aws_security_group.connector.id]
  }

  tags = merge(local.module_tags, { Name = "runcriticon-${var.environment}-database" })
}
