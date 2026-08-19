import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const accessToken = __ENV.ACCESS_TOKEN;

if (!accessToken) {
    throw new Error('ACCESS_TOKEN must be provided');
}

export const options = {
    vus: 2,
    duration: '30s',
    thresholds: {
        checks: ['rate>0.99'],
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500']
    }
};

export default function () {
    const response = http.get(
        `${baseUrl}/api/v1/transactions?page=0&size=20`,
        {
            headers: {
                Authorization: `Bearer ${accessToken}`,
                Accept: 'application/json'
            },
            tags: {
                endpoint: 'list-transactions'
            }
        }
    );

    check(response, {
        'status is 200': result => result.status === 200,
        'response is JSON': result =>
            result.headers['Content-Type']?.includes('application/json')
    });

    sleep(1);
}