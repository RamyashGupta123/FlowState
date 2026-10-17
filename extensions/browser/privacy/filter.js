export class PrivacyFilter {
    constructor(excludedDomains = []) {
        this.excludedDomains = excludedDomains.map(d => d.toLowerCase());
    }

    isDomainExcluded(domain) {
        if (!domain) return false;
        domain = domain.toLowerCase();
        return this.excludedDomains.some(d => domain === d || domain.endsWith('.' + d));
    }

    isProtectedUrl(url) {
        if (!url) return true;
        return url.startsWith('chrome://') || url.startsWith('chrome-extension://') || url.startsWith('about:') || url.startsWith('file://');
    }

    shouldTrackTab(tab) {
        if (!tab || !tab.url || tab.incognito) return false;
        if (this.isProtectedUrl(tab.url)) return false;
        
        try {
            const domain = new URL(tab.url).hostname;
            if (this.isDomainExcluded(domain)) return false;
            return true;
        } catch {
            return false;
        }
    }
}
