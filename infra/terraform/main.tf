terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.29"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

provider "kubernetes" {
  host                   = var.kube_host
  cluster_ca_certificate = base64decode(var.kube_ca)
  token                  = var.kube_token
}

module "network" {
  source = "./modules/network"

  vpc_cidr_block = var.vpc_cidr_block
}

module "cache" {
  source = "./modules/cache"

  vpc_id          = module.network.vpc_id
  subnet_ids      = module.network.private_subnet_ids
  instance_type   = var.cache_instance_type
  engine_version  = var.cache_engine_version
}

module "broker" {
  source = "./modules/broker"

  vpc_id     = module.network.vpc_id
  subnet_ids = module.network.private_subnet_ids
  broker_type = var.broker_type
}

module "eks" {
  source = "./modules/eks"

  cluster_name    = var.cluster_name
  vpc_id          = module.network.vpc_id
  subnet_ids      = module.network.private_subnet_ids
  node_group_size = var.node_group_size
}
