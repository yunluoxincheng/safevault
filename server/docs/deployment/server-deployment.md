# SafeVault Backend Server Deployment

## 1. Build Docker Image

```bash
cd server
chmod +x scripts/deploy/build-image.sh
./scripts/deploy/build-image.sh safevault-backend latest
```

## 2. Prepare Production Environment File

```bash
cp scripts/deploy/.env.prod.example scripts/deploy/.env.prod
```

Then edit `scripts/deploy/.env.prod` and set strong values for all secrets.

## 3. Deploy to Server

```bash
chmod +x scripts/deploy/deploy-server.sh
./scripts/deploy/deploy-server.sh
```

## 4. Verify

```bash
docker compose --env-file scripts/deploy/.env.prod -f docker-compose.yml ps
curl https://server.safevaultapp.top/api/swagger-ui.html
```

## 4.1 Nginx Reverse Proxy (SSL at Nginx)

Use Nginx to terminate TLS and proxy both REST and WebSocket traffic to backend `8080`.

```nginx
server {
    listen 80;
    server_name server.safevaultapp.top;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name server.safevaultapp.top;

    ssl_certificate /etc/letsencrypt/live/server.safevaultapp.top/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/server.safevaultapp.top/privkey.pem;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /api/ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 600s;
    }
}
```

## 5. Upgrade Workflow

```bash
# build new image
./scripts/deploy/build-image.sh safevault-backend v3.7.1

# update BACKEND_IMAGE in scripts/deploy/.env.prod
# BACKEND_IMAGE=safevault-backend:v3.7.1

# rolling restart with compose
./scripts/deploy/deploy-server.sh
```

## Notes

- This deployment keeps monolith runtime behavior unchanged.
- PostgreSQL and Redis run as sidecar containers in the same compose stack.
- For production hardening, place reverse proxy (Nginx/Caddy) before backend and terminate SSL there.
- Unified compose file: `docker-compose.yml` is used for production deployment.
- `docker-compose.prod.yml` is retained only as historical compatibility backup.
