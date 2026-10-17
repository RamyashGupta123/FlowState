import re

class PrivacyFilter:
    def __init__(self, excluded_apps: list[str], excluded_domains: list[str], title_collection: str = 'none'):
        self.excluded_apps = [a.lower() for a in excluded_apps]
        self.excluded_domains = [d.lower() for d in excluded_domains]
        self.title_collection = title_collection
        
    def is_app_excluded(self, process_name: str) -> bool:
        return process_name.lower() in self.excluded_apps
        
    def is_domain_excluded(self, domain: str) -> bool:
        domain = domain.lower()
        return any(domain == d or domain.endswith('.' + d) for d in self.excluded_domains)
        
    def scrub_title(self, title: str) -> str | None:
        if self.title_collection == 'none' or not title:
            return None
            
        scrubbed = title
        # email
        scrubbed = re.sub(r'[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+', '[EMAIL]', scrubbed)
        # url
        scrubbed = re.sub(r'https?://[^\s]+', '[URL]', scrubbed)
        # home dir path
        scrubbed = re.sub(r'/home/[a-zA-Z0-9_-]+', '/home/[USER]', scrubbed)
        
        return scrubbed

    def should_exclude_event(self, process_name: str, domain: str | None = None) -> bool:
        if self.is_app_excluded(process_name):
            return True
        if domain and self.is_domain_excluded(domain):
            return True
        return False
