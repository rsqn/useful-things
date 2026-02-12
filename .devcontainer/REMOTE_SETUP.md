# Remote Dev Container Setup Guide

This guide explains how to use your existing dev container configuration with various remote development services.

## Your Current Setup

Your project already has a complete dev container configuration at `.devcontainer/devcontainer.json` with:
- Amazon Corretto 21 environment
- All dependencies from `pom.xml`
- Pre-configured VS Code extensions
- Development tools (Git, GitHub CLI, Make)

## Quick Start Options

### 1. GitHub Codespaces (Recommended for GitHub Repos)

**Best for:** If your repository is on GitHub

**Setup:**
1. Push your code to GitHub (if not already)
2. Navigate to your repository on GitHub
3. Click the green "Code" button
4. Select "Codespaces" tab
5. Click "Create codespace on main" (or your branch)
6. Wait 2-3 minutes for the container to build
7. Start coding!

**Features:**
- Automatically detects and uses your `.devcontainer/devcontainer.json`
- Free tier: 60 hours/month for personal accounts
- Port forwarding for your services (8000, 8080, 3000)
- Persistent storage for your workspace

**Pricing:**
- Free: 60 hours/month (2-core, 4GB RAM)
- Paid: ~$0.18/hour for 2-core, 4GB RAM
- Larger machines available

**Access:** https://github.com/codespaces

---

### 2. GitPod

**Best for:** Cloud-first development, works with GitHub/GitLab/Bitbucket

**Setup:**
1. Go to https://gitpod.io
2. Sign in with GitHub/GitLab/Bitbucket
3. Open your repository: `https://gitpod.io/#<your-repo-url>`
4. GitPod will automatically use your `.devcontainer/devcontainer.json`

**Features:**
- Free tier: 50 hours/month
- Fast startup times
- Great for ephemeral environments
- Pre-builds your containers

**Pricing:**
- Free: 50 hours/month
- Personal: $9/month for 100 hours
- Professional: $25/month for 500 hours

**Access:** https://gitpod.io

---

### 3. Cursor Remote Development

**Best for:** Using Cursor with your existing setup

**Option A: Connect to Remote Docker Host**
1. Set up a cloud VM (AWS, DigitalOcean, etc.) with Docker
2. In Cursor: Command Palette → "Remote-SSH: Connect to Host"
3. Connect to your VM
4. Open your project folder
5. Cursor will detect and use your dev container

**Option B: Use Remote Server**
1. Set up a cloud VM with VS Code Server or Docker
2. Install Cursor on your local machine
3. Connect via SSH to the remote server
4. Open your project

**Cloud Providers:**
- **AWS EC2:** t3.small or larger (Ubuntu)
- **DigitalOcean Droplet:** $6/month (1GB RAM) or $12/month (2GB RAM)
- **Linode:** Similar pricing to DigitalOcean
- **Google Cloud Platform:** f1-micro (free tier eligible)

---

### 4. Self-Hosted Cloud VM

**Best for:** Full control, custom configurations

**Quick Setup (DigitalOcean Example):**

```bash
# 1. Create a Droplet (Ubuntu 22.04, $12/month for 2GB RAM)
# 2. SSH into the droplet
ssh root@your-droplet-ip

# 3. Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# 4. Install Docker Compose
apt-get update
apt-get install docker-compose-plugin

# 5. Clone your repository
git clone <your-repo-url>
cd useful-things

# 6. Build and run dev container
docker build -f .devcontainer/Dockerfile -t useful-things-dev .
docker run -it -v $(pwd):/workspace useful-things-dev
```

**Then connect with Cursor:**
- Use Remote-SSH extension
- Connect to your droplet
- Open the project folder

---

## Comparison Table

| Service | Setup Time | Free Tier | Best For | Works with devcontainer.json |
|---------|-----------|-----------|----------|----------------------------|
| **GitHub Codespaces** | Instant | 60 hrs/mo | GitHub repos | ✅ Automatic |
| **GitPod** | 1-2 min | 50 hrs/mo | Cloud-first dev | ✅ Automatic |
| **Cursor Remote** | 10-15 min | Self-hosted | Full control | ✅ Manual setup |
| **Self-Hosted VM** | 15-30 min | Your cost | Custom needs | ✅ Manual setup |

---

## Recommendations

### For Quick Testing:
- **GitHub Codespaces** - Just click and go (if on GitHub)

### For Regular Development:
- **GitPod** - Fast, reliable, great free tier

### For Production/Always-On:
- **Self-hosted VM** - Full control, predictable costs

### For Your Development:
Given that this is a development project with secrets and configurations:
- Consider **GitHub Codespaces** or **GitPod** for development
- Use environment variables for secrets (don't commit them)
- For production deployment, use your existing deployment scripts

---

## Environment Variables Setup

For remote development, set up secrets as environment variables:

**GitHub Codespaces:**
1. Go to Settings → Secrets and variables → Codespaces
2. Add repository secrets

**GitPod:**
1. Go to Settings → Variables
2. Add environment variables

**Remote VM:**
1. Create `.env` file (if needed)
2. Use shell environment to load

---

## Next Steps

1. **Choose a service** based on your needs
2. **Push your code** to GitHub/GitLab if using cloud services
3. **Set up secrets** as environment variables
4. **Start coding!** Your dev container will work the same way everywhere

---

## Troubleshooting

### Container won't start:
- Check `.devcontainer/devcontainer.json` syntax
- Review build logs in the service's console
- Ensure Dockerfile exists and is valid

### Dependencies not installing:
- Check `requirements.txt` and `requirements-dev.txt`
- Review `post-create.sh` script
- Check service's resource limits (may need larger instance)

### Port forwarding not working:
- Verify ports in `devcontainer.json` (8000, 8080, 3000)
- Check service's port forwarding documentation
- Ensure your services are binding to 0.0.0.0, not localhost

---

## Additional Resources

- [Dev Containers Documentation](https://containers.dev/)
- [GitHub Codespaces Docs](https://docs.github.com/en/codespaces)
- [GitPod Documentation](https://www.gitpod.io/docs)
- [VS Code Remote Development](https://code.visualstudio.com/docs/remote/remote-overview)
