import { PrivacyFilter } from '../privacy/filter.js';
import { extractDomain } from '../shared/schema.js';

test('extractDomain', () => {
    expect(extractDomain('https://github.com/path')).toBe('github.com');
});

test('PrivacyFilter should exclude domain', () => {
    const pf = new PrivacyFilter(['example.com']);
    expect(pf.isDomainExcluded('example.com')).toBe(true);
    expect(pf.isDomainExcluded('sub.example.com')).toBe(true);
    expect(pf.isDomainExcluded('other.com')).toBe(false);
});
