#!/bin/bash

# JoyAgent UI - Debug Start Script
# This script helps diagnose and start the frontend properly

set -e

# Colors for output
RED='\\033[0;31m'
GREEN='\\033[0;32m'
YELLOW='\\033[1;33m'
BLUE='\\033[0;34m'
NC='\\033[0m'

print_status() {
    echo -e "${BLUE}[UI-DEBUG]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if port is in use
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# Function to kill process on port
kill_port() {
    local port=$1
    local pid=$(lsof -ti:$port 2>/dev/null)
    if [ -n "$pid" ]; then
        print_warning "Killing existing process on port $port (PID: $pid)"
        kill -9 $pid
        sleep 2
    fi
}

# Check if we're in the right directory
check_directory() {
    if [ ! -f "package.json" ]; then
        print_error "Not in the UI directory. Please run this script from ui/ directory"
        exit 1
    fi
    
    if [ ! -f "src/components/DocumentAnalyzer.tsx" ]; then
        print_error "DocumentAnalyzer.tsx not found. Please ensure the component exists"
        exit 1
    fi
    
    print_success "Directory structure looks good"
}

# Check Node.js and npm
check_dependencies() {
    print_status "Checking dependencies..."
    
    if ! command -v node &> /dev/null; then
        print_error "Node.js is not installed"
        exit 1
    fi
    
    local node_version=$(node --version)
    print_success "Node.js version: $node_version"
    
    if ! command -v npm &> /dev/null; then
        print_error "npm is not installed"
        exit 1
    fi
    
    local npm_version=$(npm --version)
    print_success "npm version: $npm_version"
}

# Install dependencies
install_dependencies() {
    print_status "Installing/updating dependencies..."
    
    if [ "$1" = "--force" ]; then
        print_warning "Force installing dependencies..."
        rm -rf node_modules package-lock.json
    fi
    
    npm install
    
    if [ $? -eq 0 ]; then
        print_success "Dependencies installed successfully"
    else
        print_error "Failed to install dependencies"
        exit 1
    fi
}

# Clear cache and build artifacts
clear_cache() {
    print_status "Clearing cache and build artifacts..."
    
    # Clear npm cache
    npm cache clean --force
    
    # Remove build artifacts
    rm -rf dist
    rm -rf .vite
    rm -rf node_modules/.vite
    
    # Clear any TypeScript build cache
    rm -rf tsconfig.tsbuildinfo
    
    print_success "Cache cleared"
}

# Check TypeScript configuration
check_typescript() {
    print_status "Checking TypeScript configuration..."
    
    if [ ! -f "tsconfig.json" ]; then
        print_warning "No tsconfig.json found"
        return
    fi
    
    # Try to compile TypeScript
    npx tsc --noEmit --skipLibCheck
    
    if [ $? -eq 0 ]; then
        print_success "TypeScript compilation check passed"
    else
        print_warning "TypeScript compilation has issues, but continuing..."
    fi
}

# Start development server
start_dev_server() {
    print_status "Starting development server..."
    
    # Check if port 3000 is in use
    if check_port 3000; then
        print_warning "Port 3000 is already in use"
        read -p "Kill existing process and continue? (y/n): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            kill_port 3000
        else
            print_error "Cannot start server - port 3000 is in use"
            exit 1
        fi
    fi
    
    # Set environment variables for better debugging
    export NODE_ENV=development
    export BROWSER=none  # Prevent auto-opening browser
    export GENERATE_SOURCEMAP=true
    
    print_success "Starting Vite development server..."
    print_status "The server will be available at: http://localhost:3000"
    print_status "Document Analysis will be at: http://localhost:3000/documents"
    echo ""
    print_status "Press Ctrl+C to stop the server"
    echo ""
    
    # Start the server with verbose logging
    npm run dev
}

# Main function
main() {
    print_status "JoyAgent UI Debug Startup Script"
    echo ""
    
    check_directory
    check_dependencies
    
    # Parse command line arguments
    case "$1" in
        --install|-i)
            install_dependencies
            ;;
        --force-install|-f)
            install_dependencies --force
            ;;
        --clear-cache|-c)
            clear_cache
            install_dependencies
            ;;
        --check|-t)
            check_typescript
            return 0
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --install, -i        Install dependencies"
            echo "  --force-install, -f  Force reinstall all dependencies"
            echo "  --clear-cache, -c    Clear cache and reinstall dependencies"
            echo "  --check, -t          Check TypeScript compilation only"
            echo "  --help, -h           Show this help message"
            echo ""
            return 0
            ;;
        "")
            # No arguments - just start the server
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
    
    check_typescript
    start_dev_server
}

# Run main function
main "$@"