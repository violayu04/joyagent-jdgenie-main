
#!/bin/bash

# Check if Node.js version is 18
if [ $(node -v | cut -d. -f1,2) -lt 18 ]; then
  echo "Node.js version 18 is required. Current version: $(node -v)"
  exit 1
fi

# Check if pnpm is installed
if ! command -v pnpm &> /dev/null; then
  echo "pnpm is not installed. installing pnpm@7.33.1 now"
  echo "RUN 'npm install -g pnpm@7.33.1' Installing pnpm..."
  npm install pnpm@7.33.1 -g
fi

# Check if pnpm version is 7
if [ $(pnpm -v | cut -d. -f1,2) -lt 7 ]; then
  echo "pnpm version 7 is required. Current version: $(pnpm -v)"
  exit 1
fi

pnpm i --registry=https://registry.npmmirror.com

pnpm run dev

echo "✅front end code start success!"
