# DevPulse 🔍

> Automated GitHub repository health monitoring and alerting system.

DevPulse scrapes your GitHub repositories every 15 minutes, scores their **delivery health** (CI/CD success, PR cycle time, issue backlog, contributor activity), and alerts your team when something needs attention.

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)
![MongoDB](https://img.shields.io/badge/MongoDB-Atlas-green)
![AWS](https://img.shields.io/badge/AWS-EC2-yellow)
![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-blue)

---

## What it does

| Metric | How it's measured |
|--------|-------------------|
| **CI success rate** | % of GitHub Actions runs passing in last 7 days |
| **Stale PRs** | Pull requests open > 7 days with no activity |
| **PR merge time** | Average hours from open → merged |
| **Issue backlog** | Total open issues + open vs closed velocity |
| **Commit activity** | Commits per week |
| **Contributor count** | Unique contributors in last 30 days |

All metrics are combined into a **health score (0–100)** with a letter grade (A–F). Alerts fire via **email** and/or **Slack** when thresholds are crossed with a 4-hour cooldown to prevent spam.

---

## Architecture

```
GitHub API ──► @Scheduled Job ──► MetricsCollectionService ──► MongoDB
                                           │
                                    HealthScoreEngine
                                           │
                                    AlertService ──► Email / Slack
                                           │
                              REST API (Spring Boot)
                                           │
                                    EC2 via Docker
                                           ▲
                               GitHub Actions CI/CD
```

---

## Quick start (local)

### Prerequisites
- Java 17+
- Maven 3.8+
- MongoDB running locally (`brew install mongodb-community` or Docker)
- A GitHub personal access token (Settings → Developer settings → Personal access tokens)

### 1. Clone and configure

```bash
git clone https://github.com/YOUR_USERNAME/devpulse.git
cd devpulse
cp .env.example .env
# Edit .env with your values
```

### 2. Set environment variables

```bash
export GITHUB_TOKEN=ghp_your_token_here
export GITHUB_REPOS=spring-projects/spring-boot,docker/compose
export MONGODB_URI=mongodb://localhost:27017/devpulse
# Optional alerting:
export ALERT_EMAIL_ENABLED=false
export ALERT_SLACK_ENABLED=false
```

### 3. Run

```bash
mvn spring-boot:run
```

### 4. Test it

```bash
# Health check
curl http://localhost:8080/api/health

# Manually trigger a scrape
curl -X POST http://localhost:8080/api/repos/spring-projects/spring-boot/refresh

# View results
curl http://localhost:8080/api/repos/spring-projects/spring-boot | python3 -m json.tool

# View all repos
curl http://localhost:8080/api/repos
```

---

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/health` | Service health check |
| `GET` | `/api/repos` | Latest metrics for all watched repos |
| `GET` | `/api/repos/{owner}/{repo}` | Latest metrics for one repo |
| `GET` | `/api/repos/{owner}/{repo}/history` | Historical snapshots |
| `POST` | `/api/repos/{owner}/{repo}/refresh` | Manual scrape trigger |
| `GET` | `/api/alerts` | Recent alert history (last 24h) |
| `GET` | `/api/alerts/{owner}/{repo}` | Alerts for a specific repo |

---

## Deploying to AWS EC2

### 1. Launch an EC2 instance
- AMI: Amazon Linux 2023
- Instance type: t2.micro (free tier)
- Security group: open port 8080 inbound

### 2. Install Docker on EC2

```bash
sudo yum update -y
sudo yum install docker -y
sudo service docker start
sudo usermod -a -G docker ec2-user
```

### 3. Configure GitHub Secrets

In your repo → Settings → Secrets and variables → Actions, add:

| Secret | Value |
|--------|-------|
| `EC2_HOST` | Your EC2 public IP |
| `EC2_SSH_KEY` | Your EC2 private key (`.pem` contents) |
| `DOCKER_USERNAME` | Your Docker Hub username |
| `DOCKER_PASSWORD` | Your Docker Hub password |
| `GITHUB_TOKEN_PROD` | GitHub token for production scraping |
| `MONGODB_URI` | MongoDB Atlas connection string |
| `GITHUB_REPOS` | Comma-separated repos to monitor |

### 4. Push to main

```bash
git push origin main
```

GitHub Actions will automatically build, push the Docker image, and deploy to your EC2 instance.

---

## Configuration reference

| Variable | Default | Description |
|----------|---------|-------------|
| `GITHUB_TOKEN` | *required* | GitHub personal access token |
| `GITHUB_REPOS` | - | Comma-separated `owner/repo` list |
| `MONGODB_URI` | `mongodb://localhost:27017/devpulse` | MongoDB connection string |
| `SCRAPER_CRON` | `0 */15 * * * *` | How often to scrape (cron) |
| `ALERT_STALE_PR_DAYS` | `7` | Days before a PR is considered stale |
| `ALERT_HEALTH_MIN` | `60` | Score below which to alert |
| `ALERT_OPEN_ISSUES_MAX` | `50` | Open issues above which to alert |
| `ALERT_EMAIL_ENABLED` | `false` | Enable email alerts |
| `ALERT_SLACK_ENABLED` | `false` | Enable Slack alerts |
| `SLACK_WEBHOOK_URL` | - | Slack incoming webhook URL |

---

## Running tests

```bash
mvn test
```

Tests are unit-level (no database required) and run in CI on every push.

---

## Tech stack

- **Java 17** - language
- **Spring Boot 3.2** - REST API, scheduling, dependency injection
- **Spring WebFlux / WebClient** - async HTTP for GitHub API scraping
- **MongoDB / Spring Data** - metrics storage
- **GitHub Actions** - CI/CD pipeline (test → build → deploy)
- **Docker** - containerization
- **AWS EC2** - hosting

---

## Ideas for extension

- [ ] React/Next.js dashboard frontend consuming the REST API
- [ ] Webhook receiver to capture GitHub events in real-time
- [ ] DORA metrics calculation (deployment frequency, lead time, MTTR)
- [ ] Trend charts (health score over time per repo)
- [ ] Multi-user support with Spring Security

---

*Built as a portfolio project demonstrating Spring Boot, MongoDB, AWS, CI/CD, and API scraping.*
