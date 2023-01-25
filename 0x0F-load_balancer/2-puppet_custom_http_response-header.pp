<<<<<<< HEAD
# custom http header response NGiNX
exec {'update':
  command => '/usr/bin/apt-get update',
}
-> package {'nginx':
  ensure => 'present',
}
-> file_line { 'http_header':
  path  => '/etc/nginx/nginx.conf',
  match => 'http {',
  line  => "http {\n\tadd_header X-Served-By \"${hostname}\";",
}
-> exec {'run2':
  command => '/usr/sbin/service nginx start',
=======
# Automate the task of creating a custom HTTP header response, but with Puppet.
exec { 'command':
  command => 'apt-get -y update;
  apt-get -y install nginx;
  sudo sed -i "/listen 80 default_server;/a add_header X-Served-By $HOSTNAME;" /etc/nginx/sites-available/default;
  service nginx restart',
  provider => shell,
>>>>>>> f569f180ba4ca41a63a235c9bed0c86f11ecc360
}
