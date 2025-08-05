// HTTP client utility with authentication support

interface RequestOptions extends RequestInit {
  requireAuth?: boolean;
}

class HttpClient {
  private baseUrl: string;

  constructor(baseUrl: string = '') {
    this.baseUrl = baseUrl;
  }

  private getAuthHeaders(): HeadersInit {
    const token = localStorage.getItem('genie_token');
    return token ? { 'Authorization': `Bearer ${token}` } : {};
  }

  private async request<T>(
    endpoint: string,
    options: RequestOptions = {}
  ): Promise<T> {
    const { requireAuth = true, headers = {}, ...fetchOptions } = options;

    const url = `${this.baseUrl}${endpoint}`;
    const requestHeaders: HeadersInit = {
      'Content-Type': 'application/json',
      ...headers,
      ...(requireAuth ? this.getAuthHeaders() : {}),
    };

    const config: RequestInit = {
      ...fetchOptions,
      headers: requestHeaders,
    };

    try {
      const response = await fetch(url, config);
      
      // Handle authentication errors
      if (response.status === 401) {
        // Token expired or invalid
        localStorage.removeItem('genie_token');
        localStorage.removeItem('genie_user');
        window.location.reload();
        throw new Error('Authentication required');
      }

      let data;
      const contentType = response.headers.get('content-type');
      
      // Only try to parse JSON if response has JSON content
      if (contentType && contentType.includes('application/json')) {
        try {
          data = await response.json();
        } catch (parseError) {
          console.warn('Failed to parse JSON response:', parseError);
          data = {};
        }
      } else {
        data = {};
      }

      if (!response.ok) {
        throw new Error(data.message || `HTTP error! status: ${response.status}`);
      }

      return data;
    } catch (error) {
      console.error(`Request failed: ${config.method || 'GET'} ${url}`, error);
      throw error;
    }
  }

  // GET request
  async get<T>(endpoint: string, options?: RequestOptions): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: 'GET' });
  }

  // POST request
  async post<T>(
    endpoint: string,
    data?: any,
    options?: RequestOptions
  ): Promise<T> {
    return this.request<T>(endpoint, {
      ...options,
      method: 'POST',
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  // PUT request
  async put<T>(
    endpoint: string,
    data?: any,
    options?: RequestOptions
  ): Promise<T> {
    return this.request<T>(endpoint, {
      ...options,
      method: 'PUT',
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  // DELETE request
  async delete<T>(endpoint: string, options?: RequestOptions): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: 'DELETE' });
  }

  // Special method for SSE connections
  createSSEConnection(endpoint: string, options: RequestOptions = {}): EventSource {
    const { requireAuth = true } = options;
    const token = localStorage.getItem('genie_token');
    
    const url = new URL(`${this.baseUrl}${endpoint}`, window.location.origin);
    
    if (requireAuth && token) {
      url.searchParams.set('token', token);
    }

    return new EventSource(url.toString());
  }
}

// Get the appropriate base URL
const getBaseURL = (): string => {
  // Check if running in development
  if (import.meta.env?.DEV) {
    return ''; // Use proxy in development
  }
  
  // In production, use the backend URL from window object or SERVICE_BASE_URL
  return (window as any).BACKEND_URL || SERVICE_BASE_URL || 'http://localhost:8080';
};

// Create and export a default instance
const httpClient = new HttpClient(getBaseURL());
export default httpClient;

// Export the class for creating custom instances if needed
export { HttpClient };