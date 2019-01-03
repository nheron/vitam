import {Component} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {plainToClass} from 'class-transformer';
import {Title} from '@angular/platform-browser';

import {BreadcrumbService, BreadcrumbElement} from '../../../common/breadcrumb.service';
import {ReferentialsService} from '../../referentials.service';
import {PageComponent} from '../../../common/page/page-component';
import {Griffin} from './griffin';
import {ErrorService} from '../../../common/error.service';

@Component({
  selector: 'vitam-griffins',
  templateUrl: './griffins.component.html',
  styleUrls: ['./griffins.component.css']
})
export class GriffinsComponent extends PageComponent {

  newBreadcrumb: BreadcrumbElement[];
  griffin: Griffin;
  hasUnit: boolean;
  id: string;
  panelHeader: string;

  constructor(private activatedRoute: ActivatedRoute, public router: Router,
              public titleService: Title, public breadcrumbService: BreadcrumbService,
              private searchReferentialsService: ReferentialsService, private errorService: ErrorService) {
    super('Détail du Griffon', [], titleService, breadcrumbService);

  }

  pageOnInit() {
    this.activatedRoute.params.subscribe(params => {
      this.id = params['id'];
    });
  }
}