import {Component, OnInit} from '@angular/core';
import {AuthenticationService} from './authentication.service';
import {Router} from '@angular/router';

@Component({
  selector: 'vitam-authentication',
  templateUrl: './authentication.component.html',
  styleUrls: ['./authentication.component.css']
})
export class AuthenticationComponent implements OnInit {

  needCertificate = false;
  showLoginForm = false;
  showLoginErrorMessage = false;
  username: string;
  password: string;

  constructor(private authenticationService: AuthenticationService, private router: Router) {
  }

  ngOnInit() {

    this.authenticationService.getSecureMode().subscribe(data => {
      this.authenticationService.verifyAuthentication().subscribe(
        () => {
          this.router.navigate(["admin/collection"]);
          this.authenticationService.loggedIn();
        },
        (error) => {
          if (data.body.indexOf('x509') > -1) {
            this.needCertificate = true;
          } else {
            this.showLoginForm = true;
          }
          this.authenticationService.loggedOut();
        }
      )
    }, (error) => {
      this.needCertificate = true;
    });
  }

  logIn() {
    this.authenticationService.logIn(this.username, this.password).subscribe(response => {
      this.authenticationService.loggedIn();
      this.router.navigate(["admin/collection"]);
    }, () => {
      this.showLoginErrorMessage = true;

    });
  }

}
