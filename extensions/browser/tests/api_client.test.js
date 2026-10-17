import { parseJwt, isTokenExpired } from '../sync/api_client.js';

describe('ApiClient JWT validation', () => {
    test('isTokenExpired returns true for null, empty or invalid tokens', () => {
        expect(isTokenExpired(null)).toBe(true);
        expect(isTokenExpired('')).toBe(true);
        expect(isTokenExpired('not.a.token')).toBe(true);
        expect(isTokenExpired('abc.def')).toBe(true);
    });

    test('isTokenExpired returns true for expired token', () => {
        // Expiration in the past (1000 seconds after Unix epoch)
        const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64');
        const payload = Buffer.from(JSON.stringify({ exp: 1000, sub: 'user-1' })).toString('base64');
        const token = `${header}.${payload}.sig`;
        expect(isTokenExpired(token)).toBe(true);
    });

    test('isTokenExpired returns false for token valid in future', () => {
        const futureExp = Math.floor(Date.now() / 1000) + 3600; // 1 hour ahead
        const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64');
        const payload = Buffer.from(JSON.stringify({ exp: futureExp, sub: 'user-1' })).toString('base64');
        const token = `${header}.${payload}.sig`;
        expect(isTokenExpired(token)).toBe(false);
    });

    test('parseJwt extracts claims correctly', () => {
        const header = Buffer.from(JSON.stringify({ alg: 'HS256' })).toString('base64');
        const payload = Buffer.from(JSON.stringify({ sub: 'test-user', email: 'test@flowstate.com' })).toString('base64');
        const token = `${header}.${payload}.sig`;
        const claims = parseJwt(token);
        expect(claims).not.toBeNull();
        expect(claims.sub).toBe('test-user');
        expect(claims.email).toBe('test@flowstate.com');
    });
});
