#!/bin/bash

# JoyAgent - Stop Script
# This script stops all JoyAgent services including document analysis

set -e

# Colors for output
RED='\\033[0;31m'
GREEN='\\033[0;32m'
YELLOW='\\033[1;33m'
BLUE='\\033[0;34m'
NC='\\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e \"${BLUE}[JoyAgent]${NC} $1\"
}

print_success() {
    echo -e \"${GREEN}[SUCCESS]${NC} $1\"
}

print_warning() {
    echo -e \"${YELLOW}[WARNING]${NC} $1\"
}

print_error() {
    echo -e \"${RED}[ERROR]${NC} $1\"
}

# Function to stop service by PID file
stop_service_by_pid() {
    local pid_file=$1
    local service_name=$2
    
    if [ -f \"$pid_file\" ]; then
        local pid=$(cat \"$pid_file\")
        if kill -0 \"$pid\" 2>/dev/null; then
            print_status \"Stopping $service_name (PID: $pid)...\"
            kill \"$pid\"
            sleep 2
            
            # Force kill if still running
            if kill -0 \"$pid\" 2>/dev/null; then
                print_warning \"Force killing $service_name...\"
                kill -9 \"$pid\"
            fi
            
            print_success \"$service_name stopped\"
        else
            print_warning \"$service_name was not running\"
        fi
        rm -f \"$pid_file\"
    else
        print_warning \"No PID file found for $service_name\"
    fi
}

# Function to stop service by port
stop_service_by_port() {
    local port=$1
    local service_name=$2
    
    local pid=$(lsof -ti:$port 2>/dev/null)
    if [ -n \"$pid\" ]; then
        print_status \"Stopping $service_name on port $port (PID: $pid)...\"
        kill \"$pid\"
        sleep 2
        
        # Force kill if still running
        local still_running=$(lsof -ti:$port 2>/dev/null)
        if [ -n \"$still_running\" ]; then
            print_warning \"Force killing $service_name...\"
            kill -9 \"$still_running\"
        fi
        
        print_success \"$service_name stopped\"
    else
        print_warning \"$service_name was not running on port $port\"
    fi
}

# Main function
main() {
    print_status \"Stopping JoyAgent services...\"
    
    # Create logs directory if it doesn't exist
    mkdir -p logs
    
    # Stop services by PID files first
    stop_service_by_pid \"logs/frontend.pid\" \"React Frontend\"
    stop_service_by_pid \"logs/backend.pid\" \"Java Backend\"
    stop_service_by_pid \"logs/document-service.pid\" \"Document Analysis Service\"
    
    # Also try to stop by ports as backup
    stop_service_by_port 3000 \"Frontend Service\"
    stop_service_by_port 8080 \"Backend Service\"
    stop_service_by_port 8188 \"Document Analysis Service\"
    
    # Stop any remaining Java processes related to Genie
    print_status \"Checking for remaining Genie processes...\"
    local genie_pids=$(ps aux | grep -i genie | grep -v grep | awk '{print $2}' || true)
    if [ -n \"$genie_pids\" ]; then
        print_status \"Found remaining Genie processes: $genie_pids\"
        echo \"$genie_pids\" | xargs kill -9 2>/dev/null || true
        print_success \"Remaining Genie processes terminated\"
    fi
    
    # Clean up log files if requested
    if [ \"$1\" = \"--clean-logs\" ] || [ \"$1\" = \"-c\" ]; then
        print_status \"Cleaning up log files...\"
        rm -f logs/*.log
        rm -f logs/*.pid
        print_success \"Log files cleaned\"
    fi
    
    # Clean up temporary files
    print_status \"Cleaning up temporary files...\"
    rm -rf uploads/temp/*
    
    echo \"\"
    print_success \"🛑 All JoyAgent services have been stopped\"
    echo \"\"
    
    if [ \"$1\" != \"--clean-logs\" ] && [ \"$1\" != \"-c\" ]; then
        echo \"Note: Log files are preserved in ./logs/ directory\"
        echo \"Use '$0 --clean-logs' to remove them\"
    fi
}

# Handle command line arguments
case \"$1\" in
    --help|-h)
        echo \"JoyAgent Stop Script\"
        echo \"\"
        echo \"Usage: $0 [OPTIONS]\"
        echo \"\"
        echo \"Options:\"
        echo \"  --clean-logs, -c     Remove log files after stopping services\"
        echo \"  --help, -h          Show this help message\"
        echo \"\"
        exit 0
        ;;
    *)
        main \"$1\"
        ;;
esac