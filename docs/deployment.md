# Deploying DeployTrack to AWS

End-to-end runbook, from no AWS account to a running application, and the
teardown that stops it costing anything.

Everything below runs on your machine with your credentials. Read the cost
section before you start.

---

## What gets created

| Resource | Why | Free tier |
|---|---|---|
| VPC, subnets, internet gateway, security groups | Network isolation | Free |
| EC2 `t3.micro` (ARM) | Runs both containers | 750 hrs/month for 12 months |
| RDS `db.t3.micro`, 20GB | Managed PostgreSQL | 750 hrs/month for 12 months |
| Elastic IP | Stable address across restarts | Free **while attached** |
| ECR, 2 repositories | Container images | 500MB/month |
| SSM parameters | Secrets, encrypted at rest | Free (SecureString) |
| IAM roles | Credential-free access | Free |

**Deliberately not created**, because each is a common source of surprise bills
and none is needed at this scale:

- **NAT Gateway** — ~£28/month. RDS makes no outbound calls, so nothing needs it.
- **Application Load Balancer** — ~£16/month. One instance does not need
  balancing; Nginx already terminates HTTP.
- **Multi-AZ RDS** — doubles the database cost for failover this does not need.

Running total inside free tier: **about £0/month**, plus pennies if ECR storage
exceeds 500MB. After 12 months, roughly **£19/month** if left running.

---

## 1. AWS account

Sign-up needs a card and identity verification, so this part is yours.

1. Create an account at https://aws.amazon.com — a card is required even on
   free tier, and small verification charges are refunded.
2. **Enable MFA on the root user immediately.** IAM → Security credentials →
   assign an authenticator app. The root user can close the account and change
   billing; it is the one login worth protecting properly.
3. **Set a billing alert before creating anything.** Billing → Budgets →
   Create budget → Zero spend budget, or a cost budget at a figure you are
   comfortable with (£5 is sensible). This is the difference between noticing
   an accidental charge in a day and noticing it in a month.

## 2. An IAM user for Terraform

Never use root credentials for day-to-day work — they cannot be scoped or
easily revoked.

1. IAM → Users → Create user, name it `terraform`.
2. Attach `AdministratorAccess`. Broad, but Terraform genuinely creates IAM
   roles, VPCs and RDS instances; a narrower policy here is a project of its own.
3. Create user → Security credentials → Create access key → **Command Line
   Interface**.
4. Copy both values. The secret is shown once.

## 3. Install the tooling

```bash
brew install awscli terraform
```

Then configure the CLI with the key from the previous step. Run this yourself —
the credentials should not pass through anyone else's hands:

```bash
aws configure
```

Confirm it worked:

```bash
aws sts get-caller-identity
```

## 4. Provision the infrastructure

```bash
cd terraform && terraform init
```

Review what will be created before creating it. This is the habit worth
forming — `plan` is how you catch a change you did not intend:

```bash
terraform plan
```

Then apply. It takes roughly 10 minutes, almost all of it waiting for RDS:

```bash
terraform apply
```

The outputs include the application URL, the ECR repository addresses, the
instance id, and the command to read the generated admin password.

## 5. Point GitHub at the infrastructure

The deploy workflow reads these as **repository variables**, not secrets —
none is sensitive, and variables are visible in logs, which helps when
debugging a failed deploy.

Settings → Secrets and variables → Actions → Variables → New variable:

| Variable | Value |
|---|---|
| `AWS_DEPLOY_ROLE_ARN` | `terraform output -raw github_deploy_role_arn` |
| `AWS_REGION` | `eu-west-2`, or whichever you chose |
| `AWS_INSTANCE_ID` | `terraform output -raw instance_id` |
| `AWS_APP_HOST` | The IP from `terraform output -raw application_url` |

There is no AWS access key here. GitHub authenticates through OIDC, exchanging
a short-lived signed token for temporary credentials — nothing permanent is
stored, so nothing permanent can leak.

## 6. Deploy

Push to `main`, or trigger it by hand:

```bash
gh workflow run deploy.yml
```

The workflow builds both images for ARM, pushes them to ECR, sends the compose
file and a pull-and-restart command to the instance through SSM, and then polls
the application until it answers. A deploy that starts containers but serves
errors fails the workflow rather than reporting green.

## 7. Check it works

```bash
terraform output -raw application_url
```

Open it and use **Continue to the demo**. To sign in as the admin, read the
generated password — it is never printed to a terminal or a log by default:

```bash
aws ssm get-parameter --name /deploytrack/admin-password --with-decryption --query Parameter.Value --output text
```

---

## When something is wrong

**Get a shell without opening SSH.** Session Manager works through the IAM
role, so port 22 stays closed and every session is recorded in CloudTrail:

```bash
aws ssm start-session --target "$(terraform output -raw instance_id)"
```

**The instance came up but nothing is serving.** Bootstrap output lands here,
and is the first place to look:

```bash
sudo tail -100 /var/log/cloud-init-output.log
```

**Containers are not running.**

```bash
cd /opt/deploytrack && sudo docker compose -f docker-compose.prod.yml ps
```

**The backend is restarting.** Almost always the database connection or a
missing secret. The prod profile has no fallbacks by design, so a missing
variable stops startup rather than silently using a development default:

```bash
sudo docker logs deploytrack-backend --tail 50
```

---

## Tearing it down

**Do this when you are finished.** An Elastic IP left unattached bills at about
£3/month, and free tier expires after 12 months.

```bash
cd terraform && terraform destroy
```

It removes everything this stack created, including the database and its data.
ECR repositories are set to `force_delete` and RDS to `skip_final_snapshot`
specifically so `destroy` completes rather than failing partway and leaving
billable resources behind.

Afterwards, confirm nothing survived — Terraform only knows about what it
created, so this catches anything added by hand:

```bash
aws ec2 describe-instances --query 'Reservations[].Instances[?State.Name!=`terminated`].[InstanceId,State.Name]' --output table
```

```bash
aws ec2 describe-addresses --query 'Addresses[].[PublicIp,AssociationId]' --output table
```

An address listed with no association is costing money. Release it.

---

## Notes on the design

**RDS has no route to the internet.** It lives in private subnets, and its
security group accepts traffic only from the application's security group —
not from a CIDR range, so the rule survives the instance being replaced. A
publicly accessible database is found by scanners within minutes of appearing.

**Nothing stores AWS credentials.** The instance uses an IAM role; GitHub uses
OIDC. Both issue temporary credentials that rotate automatically. Long-lived
access keys on a server or in CI are the most common way AWS accounts are
compromised.

**IMDSv2 is required.** Version 1 of the instance metadata service is what made
server-side request forgery able to steal role credentials. Requiring a token
closes that path.

**Secrets never touch the repository or an image.** They are generated by
Terraform, stored encrypted in Parameter Store, and read at boot by the
instance. Rotating one means updating a parameter and restarting — no rebuild.

**Log rotation is configured.** Uncapped container logs grow until they fill
the root volume and take the instance down, which is a genuinely common way
small deployments die weeks after launch.
