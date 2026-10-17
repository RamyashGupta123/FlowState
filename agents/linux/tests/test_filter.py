from privacy.filter import PrivacyFilter

def test_app_excluded():
    pf = PrivacyFilter(excluded_apps=['keepassxc'], excluded_domains=[])
    assert pf.is_app_excluded('keepassxc')
    assert pf.is_app_excluded('KeepassXC')
    assert not pf.is_app_excluded('code')

def test_domain_excluded():
    pf = PrivacyFilter(excluded_apps=[], excluded_domains=['example.com'])
    assert pf.is_domain_excluded('example.com')
    assert pf.is_domain_excluded('sub.example.com')
    assert not pf.is_domain_excluded('otherexample.com')

def test_scrub_title():
    pf = PrivacyFilter([], [], 'scrubbed')
    assert pf.scrub_title('Editing user@email.com') == 'Editing [EMAIL]'
    assert pf.scrub_title('Browsing https://secret.com') == 'Browsing [URL]'
    assert pf.scrub_title('File /home/user/doc.txt') == 'File /home/[USER]/doc.txt'
    
    pf_none = PrivacyFilter([], [], 'none')
    assert pf_none.scrub_title('Editing user@email.com') is None
