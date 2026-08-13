#!/bin/bash
# Runs once on first boot, as root, via cloud-init.
#
# Output goes to /var/log/cloud-init-output.log on the instance -- that is the
# first place to look when the application does not come up.
set -euxo pipefail

dnf update -y
dnf install -y docker

systemctl enable --now docker

# Compose v2 ships as a Docker CLI plugin rather than a package on Amazon Linux
# 2023, so it is installed by hand. uname -m resolves to aarch64 on the ARM
# instance types this stack uses.
DOCKER_CONFIG=/usr/local/lib/docker
mkdir -p "$DOCKER_CONFIG/cli-plugins"
curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
  -o "$DOCKER_CONFIG/cli-plugins/docker-compose"
chmod +x "$DOCKER_CONFIG/cli-plugins/docker-compose"

install -d -m 0755 /opt/deploytrack

# Secrets are read at boot from Parameter Store using the instance's IAM role.
# Nothing sensitive is baked into the image, written into user data, or stored
# in the repository -- and rotating one means updating a parameter and
# restarting, not rebuilding anything.
REGION="${aws_region}"
get_param() {
  aws ssm get-parameter --name "$1" --with-decryption \
    --query 'Parameter.Value' --output text --region "$REGION"
}

DB_PASSWORD="$(get_param "/${project_name}/db-password")"
JWT_SECRET="$(get_param "/${project_name}/jwt-secret")"
ADMIN_PASSWORD="$(get_param "/${project_name}/admin-password")"
DEMO_PASSWORD="$(get_param "/${project_name}/demo-password")"

# Written with restrictive permissions before any value goes into it, so the
# secrets are never briefly world-readable.
install -m 0600 /dev/null /opt/deploytrack/.env
cat > /opt/deploytrack/.env <<ENVFILE
AWS_REGION=${aws_region}
ECR_REGISTRY=${account_id}.dkr.ecr.${aws_region}.amazonaws.com
PROJECT_NAME=${project_name}

DB_HOST=${db_host}
DB_PORT=5432
DB_NAME=${db_name}
DB_USER=${db_user}
DB_PASSWORD=$DB_PASSWORD

JWT_SECRET=$JWT_SECRET
ADMIN_EMAIL=${admin_email}
ADMIN_PASSWORD=$ADMIN_PASSWORD
DEMO_PASSWORD=$DEMO_PASSWORD
ENVFILE

aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "${account_id}.dkr.ecr.${aws_region}.amazonaws.com"

# A systemd unit rather than a bare `docker compose up`, so the stack comes
# back automatically after a reboot or an instance stop/start instead of
# needing someone to log in and restart it.
cat > /etc/systemd/system/deploytrack.service <<'UNIT'
[Unit]
Description=DeployTrack
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/deploytrack
ExecStart=/usr/bin/docker compose -f docker-compose.prod.yml up -d
ExecStop=/usr/bin/docker compose -f docker-compose.prod.yml down

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable deploytrack.service

# The compose file arrives with the first deployment, so the service is enabled
# now and started by the deploy workflow rather than here. Starting it before
# the file exists would only fail loudly for no benefit.
echo "Instance bootstrap complete. Awaiting first deployment."
