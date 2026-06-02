# Outputs del módulo network, consumidos por database (Bloque 2B) y runtime (Bloque 4).

output "vpc_id" {
  description = "ID de la VPC."
  value       = aws_vpc.this.id
}

output "vpc_cidr" {
  description = "CIDR de la VPC."
  value       = aws_vpc.this.cidr_block
}

output "private_subnet_ids" {
  description = "IDs de las subnets privadas (RDS, VPC connector)."
  value       = aws_subnet.private[*].id
}

output "public_subnet_ids" {
  description = "IDs de las subnets públicas (NAT)."
  value       = aws_subnet.public[*].id
}

output "database_security_group_id" {
  description = "SG que protege RDS (solo acepta del connector)."
  value       = aws_security_group.database.id
}

output "connector_security_group_id" {
  description = "SG del VPC connector de App Runner (lo usa runtime en Bloque 4)."
  value       = aws_security_group.connector.id
}

output "nat_gateway_id" {
  description = "ID del NAT Gateway."
  value       = aws_nat_gateway.this.id
}
