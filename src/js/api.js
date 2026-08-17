import { showSpinner, hideSpinner } from './spinner.js';
import { showToast } from './toast-notifier.js';

const API_BASE_URL = 'http://localhost:8080/api';

export async function fetchData(endpoint, options = {}) {
    showSpinner();
    try {
        const response = await fetch(`${API_BASE_URL}${endpoint}`, options);
        hideSpinner();

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({ message: response.statusText }));
            showToast(`Error: ${errorData.message || response.statusText}`, 'error');
            throw new Error(errorData.message || response.statusText);
        }

        return await response.json();
    } catch (error) {
        hideSpinner();
        console.error('API Error:', error);
        showToast(`Request failed: ${error.message}`, 'error');
        throw error;
    }
}
