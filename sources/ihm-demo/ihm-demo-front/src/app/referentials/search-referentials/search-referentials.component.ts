import {Component, EventEmitter} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {Title} from '@angular/platform-browser';

import {BreadcrumbService, BreadcrumbElement} from "../../common/breadcrumb.service";
import {VitamResponse} from "../../common/utils/response";
import {PageComponent} from "../../common/page/page-component";
import {Preresult} from '../../common/search/preresult';
import {FieldDefinition} from '../../common/search/field-definition';
import {DateService} from '../../common/utils/date.service';
import {ColumnDefinition} from '../../common/generic-table/column-definition';
import {ReferentialsService} from "./../referentials.service";
import {ArchiveUnitHelper} from "../../archive-unit/archive-unit.helper";
import { AuthenticationService } from '../../authentication/authentication.service';

@Component({
  selector: 'vitam-search-referentials',
  templateUrl: './search-referentials.component.html',
  styleUrls: ['./search-referentials.component.css']
})
//TODO Revoir si on doit éclater le component
export class SearchReferentialsComponent extends PageComponent {

  referentialType: string;
  breadcrumbName: string;
  referentialPath: string;
  referentialIdentifier: string;
  public response: VitamResponse;
  public searchForm: any = {};
  initialSortKey: string;
  searchButtonLabel: string;
  isImportable :boolean;
  specificTitle: string;

  referentialData = [];
  public columns = [];
  public extraColumns = [];


  constructor(private activatedRoute: ActivatedRoute, private router: Router, private authenticationService : AuthenticationService,
              public titleService: Title, public breadcrumbService: BreadcrumbService,
              private searchReferentialsService: ReferentialsService, private archiveUnitHelper: ArchiveUnitHelper) {
    super('Recherche du référentiel', [], titleService, breadcrumbService);

    this.activatedRoute.params.subscribe(params => {
      this.referentialType = params['referentialType'];
      let newBreadcrumb = [];
      this.isImportable = true;
      this.searchButtonLabel = '';
      this.specificTitle = 'Recherche du référentiel';
      switch (this.referentialType) {
        case "accessContract":
          this.searchReferentialsService.setSearchAPI('accesscontracts');
          this.breadcrumbName = "Contrats d'accès";
          this.specificTitle = 'Contrats d\'accès';
          this.referentialData = [
            new FieldDefinition('ContractName', "Intitulé", 6, 8),
            FieldDefinition.createIdField('ContractID', "Identifiant", 6, 8)
          ];
          this.searchForm = {
            "ContractID": "all",
            "ContractName": "all",
            "orderby": {"field": "Name", "sortType": "ASC"}
          };
          this.initialSortKey = "Name";
          this.columns = [
            ColumnDefinition.makeStaticColumn('Name', 'Intitulé', undefined,
              () => ({'width': '325px'})),
            ColumnDefinition.makeStaticColumn('Identifier', 'Identifiant', undefined,
              () => ({'width': '225px'})),
            ColumnDefinition.makeStaticColumn('#tenant', 'Tenant', undefined,
              () => ({'width': '63px'})),
            ColumnDefinition.makeStaticColumn('Status', 'Statut', SearchReferentialsComponent.handleStatus,
              () => ({'width': '62px'})),
            ColumnDefinition.makeStaticColumn('CreationDate', 'Date de création', DateService.handleDate,
              () => ({'width': '125px'})),
            ColumnDefinition.makeStaticColumn('LastUpdate', 'Dernière modification', DateService.handleDate,
              () => ({'width': '125px'}))
          ];
          this.extraColumns = [];
          this.referentialPath = 'admin/accessContract';
          this.referentialIdentifier = 'Identifier';
          break;
        case "ingestContract":
          this.searchReferentialsService.setSearchAPI('contracts');
          this.breadcrumbName = "Contrats d'entrée";
          this.specificTitle = 'Contrats d\'entrée';
          this.referentialData = [
            new FieldDefinition('ContractName', "Intitulé", 6, 8),
            FieldDefinition.createIdField('ContractID', "Identifiant", 6, 8)
          ];
          this.searchForm = {
            "ContractID": "all",
            "ContractName": "all",
            "orderby": {"field": "Name", "sortType": "ASC"}
          };
          this.initialSortKey = "Name";
          this.columns = [
            ColumnDefinition.makeStaticColumn('Name', 'Intitulé', undefined,
              () => ({'width': '325px'})),
            ColumnDefinition.makeStaticColumn('Identifier', 'Identifiant', undefined,
              () => ({'width': '225px'})),
            ColumnDefinition.makeStaticColumn('#tenant', 'Tenant', undefined,
              () => ({'width': '63px'})),
            ColumnDefinition.makeStaticColumn('Status', 'Statut', SearchReferentialsComponent.handleStatus,
              () => ({'width': '62px'})),
            ColumnDefinition.makeStaticColumn('CreationDate', 'Date de création', DateService.handleDate,
              () => ({'width': '125px'})),
            ColumnDefinition.makeStaticColumn('LastUpdate', 'Dernière modification', DateService.handleDate,
              () => ({'width': '125px'}))
          ];
          this.extraColumns = [];
          this.referentialPath = 'admin/ingestContract';
          this.referentialIdentifier = 'Identifier';
          break;
        case "format":
          this.searchReferentialsService.setSearchAPI('admin/formats');
          this.breadcrumbName = "Formats";
          this.specificTitle = 'Formats';
          this.referentialData = [
            new FieldDefinition('FormatName', "Intitulé", 6, 8),
            FieldDefinition.createIdField('PUID', "PUID", 6, 8)
          ];
          this.searchForm = {
            "FormatName": "",
            "PUID": "",
            "orderby": {"field": "Name", "sortType": "ASC"},
            "FORMAT": "all"
          };
          this.initialSortKey = "Name";
          this.columns = [
            ColumnDefinition.makeStaticColumn('PUID', 'PUID', undefined,
              () => ({'width': '125px'})),
            ColumnDefinition.makeStaticColumn('Name', 'Intitulé', undefined,
              () => ({'width': '325px'})),
            ColumnDefinition.makeStaticColumn('Version', 'Version', undefined,
              () => ({'width': '125px'})),
            ColumnDefinition.makeStaticColumn('MIMEType', 'MIME', undefined,
              () => ({'width': '125px'})),
            ColumnDefinition.makeStaticColumn('Extension', 'Extension(s)', undefined,
              () => ({'width': '125px'}))
          ];
          this.extraColumns = [];
          this.referentialPath = 'admin/format';
          this.referentialIdentifier = 'PUID';
          if (!this.authenticationService.isTenantAdmin()) {
            this.isImportable = false;
          }
          break;
        case "rule":
          this.searchReferentialsService.setSearchAPI('admin/rules');
          this.breadcrumbName = "Règles de gestion";
          this.specificTitle = 'Règles de gestion';
          let options = [
            {label: "Tous", value: "All"},
            {label: "Durée d'utilité administrative", value: "AppraisalRule"},
            {label: "Délai de communicabilité", value: "AccessRule"},
            {label: "Durée d'utilité courante", value: "StorageRule"},
            {label: "Délai de diffusion", value: "DisseminationRule"},
            {label: "Durée de réutilisation", value: "ReuseRule"},
            {label: "Durée de classification", value: "ClassificationRule"}
          ];
          this.referentialData = [
            new FieldDefinition('RuleValue', "Intitulé", 6, 8),
            FieldDefinition.createSelectMultipleField('RuleType', "Type", options, 6, 8)
          ];
          this.searchForm = {"RuleValue": "", "RuleType": "All", "RULES": "all"};
          this.initialSortKey = "RuleValue";
          this.columns = [
            ColumnDefinition.makeStaticColumn('RuleValue', 'Intitulé', undefined,
              () => ({'width': '325px'})),
            ColumnDefinition.makeStaticColumn('RuleType', 'Type', SearchReferentialsComponent.ruleToLabel(archiveUnitHelper.rulesCategories),
              () => ({'width': '125px'})),
            ColumnDefinition.makeSpecialValueColumn('Durée', SearchReferentialsComponent.appendUnitToRuleDuration,
              undefined, () => ({'width': '125px'}), 'custom', this.sortableDuration, 'dureeField'),
            ColumnDefinition.makeStaticColumn('RuleDescription', 'Description', undefined,
              () => ({'width': '325px'})),
            ColumnDefinition.makeStaticColumn('RuleId', 'Identifiant', undefined,
              () => ({'width': '125px'}))
          ];
          this.extraColumns = [];
          this.referentialPath = 'admin/rule';
          this.referentialIdentifier = 'RuleId';
          break;
        case "profil":
          this.searchReferentialsService.setSearchAPI('profiles');
          this.breadcrumbName = "Profils d'archivage";
          this.specificTitle = 'Profils d\'archivage';
          this.referentialData = [
            new FieldDefinition('ProfileName', "Intitulé", 6, 8),
            FieldDefinition.createIdField('ProfileID', "Identifiant", 6, 8)
          ];
          this.searchForm = {"ProfileID": "all", "ProfileName": "all", "orderby": {"field": "Name", "sortType": "ASC"}};
          this.initialSortKey = "Name";
          this.columns = [
            ColumnDefinition.makeStaticColumn('Name', 'Intitulé', undefined,
              () => ({'width': '325px'})),
            ColumnDefinition.makeStaticColumn('Identifier', 'Identifiant', undefined,
              () => ({'width': '125px'})),
            ColumnDefinition.makeStaticColumn('Status', 'Statut', SearchReferentialsComponent.handleStatus,
              () => ({'width': '60px'})),
            ColumnDefinition.makeStaticColumn('CreationDate', 'Date de création', DateService.handleDate,
              () => ({'width': '100px'})),
            ColumnDefinition.makeStaticColumn('LastUpdate', 'Dernière modification', DateService.handleDate,
              () => ({'width': '100px'})),
            ColumnDefinition.makeIconColumn('Profil', ['fa-download'],
              SearchReferentialsComponent.downloadProfil, SearchReferentialsComponent.checkProfil,
              () => ({'width': '50px'}), this.searchReferentialsService)
          ];
          this.extraColumns = [];
          this.referentialPath = 'admin/profil';
          this.referentialIdentifier = 'Identifier';
          break;
        case "archiveUnitProfile":
          this.searchReferentialsService.setSearchAPI('archiveunitprofiles');
          this.breadcrumbName = "Documents type";
          this.specificTitle = 'Documents type';
          this.referentialData = [
            new FieldDefinition('ArchiveUnitProfileName', "Intitulé", 6, 8),
            FieldDefinition.createIdField('ArchiveUnitProfileID', "Identifiant", 6, 8)
          ];
          this.searchForm = {"ArchiveUnitProfileID": "all", "ArchiveUnitProfileName": "all",
            "orderby": {"field": "Name", "sortType": "ASC"}};
          this.initialSortKey = "Name";
          this.columns = [
            ColumnDefinition.makeStaticColumn('Name', 'Intitulé', undefined,
              () => ({'width': '325px'})),
            ColumnDefinition.makeStaticColumn('Identifier', 'Identifiant', undefined,
              () => ({'width': '125px'})),
            ColumnDefinition.makeStaticColumn('Status', 'Statut', SearchReferentialsComponent.handleStatus,
              () => ({'width': '60px'})),
            ColumnDefinition.makeStaticColumn('CreationDate', 'Date de création', DateService.handleDate,
              () => ({'width': '100px'})),
            ColumnDefinition.makeStaticColumn('LastUpdate', 'Dernière modification', DateService.handleDate,
              () => ({'width': '100px'})),
            ColumnDefinition.makeIconColumn('Document', ['fa-download'],
              SearchReferentialsComponent.downloadArchiveUnitProfile, SearchReferentialsComponent.checkArchiveUnitProfile,
              () => ({'width': '50px'}), this.searchReferentialsService)
          ];
          this.extraColumns = [];
          this.referentialPath = 'admin/archiveUnitProfile';
          this.referentialIdentifier = 'Identifier';
          break;
        case "context":
          this.searchReferentialsService.setSearchAPI('contexts');
          this.breadcrumbName = "Contextes applicatifs";
          this.specificTitle = 'Contextes applicatifs';
          this.referentialData = [
            new FieldDefinition('ContextName', "Intitulé", 6, 8),
            FieldDefinition.createIdField('ContextID', "Identifiant", 6, 8)
          ];
          this.searchForm = {"ContextID": "all", "ContextName": "all", "orderby": {"field": "Name", "sortType": "ASC"}};
          this.initialSortKey = "Name";
          this.columns = [
            ColumnDefinition.makeStaticColumn('Name', 'Intitulé', undefined,
              () => ({'width': '325px'})),
            ColumnDefinition.makeStaticColumn('Identifier', 'Identifiant', undefined,
              () => ({'width': '125px'})),
            ColumnDefinition.makeStaticColumn('Status', 'Statut', SearchReferentialsComponent.handleStatus,
              () => ({'width': '125px'})),
            ColumnDefinition.makeSpecialIconColumn("Contrat d'accès",
              SearchReferentialsComponent.checkAccessContract, undefined,
              () => ({'width': '125px'})),
            ColumnDefinition.makeSpecialIconColumn("Contrat d'entrée",
              SearchReferentialsComponent.checkIngestContract, undefined,
              () => ({'width': '125px'})),
            ColumnDefinition.makeStaticColumn('CreationDate', "Date de création", DateService.handleDate,
              () => ({'width': '125px'})),
            ColumnDefinition.makeStaticColumn('LastUpdate', "Dernière modification", DateService.handleDate,
              () => ({'width': '125px'}))
          ];
          this.extraColumns = [
            ColumnDefinition.makeStaticColumn('#id', 'GUID', undefined,
              () => ({'width': '325px'}))
          ];
          this.referentialPath = 'admin/context';
          this.referentialIdentifier = 'Identifier';
          if (!this.authenticationService.isTenantAdmin()) {
            this.isImportable = false;
          }
          break;

        case "agencies":
          this.searchReferentialsService.setSearchAPI('agencies');
          this.breadcrumbName = "Services agents";
          this.specificTitle = 'Services agents';
          this.referentialData = [
            new FieldDefinition('AgencyName', "Intitulé", 4, 10),
            FieldDefinition.createIdField('AgencyID', "Identifiant", 4, 10),
            new FieldDefinition('Description', "Description", 4, 10)
          ];
          this.searchForm = {"AgencyID": "all", "AgencyName": "all", "orderby": {"field": "Name", "sortType": "ASC"}};
          this.initialSortKey = "Name";
          this.columns = [
            ColumnDefinition.makeStaticColumn('Name', 'Intitulé', undefined,
              () => ({'width': '125px'})),
            ColumnDefinition.makeStaticColumn('Description', 'Description', undefined,
              () => ({'width': '225px'})),
            ColumnDefinition.makeStaticColumn('Identifier', 'Identifiant', undefined,
              () => ({'width': '125px'}))
          ];
          this.referentialPath = 'admin/agencies/all';
          this.referentialIdentifier = 'Identifier';
          break;


        case "accession-register":
          this.searchReferentialsService.setSearchAPI('admin/accession-register');
          this.breadcrumbName = "Recherche par service producteur";
          this.specificTitle = 'Services producteurs';
          this.referentialData = [
            new FieldDefinition('OriginatingAgency', "Service producteur", 12, 4),
          ];
          this.searchForm = {
            "ACCESSIONREGISTER": "ACCESSIONREGISTER",
            "orderby": {"field": "OriginatingAgency", "sortType": "ASC"}
          };
          this.initialSortKey = "OriginatingAgency";
          this.columns = [
            ColumnDefinition.makeStaticColumn('OriginatingAgency', 'Service producteur', undefined,
              () => ({'width': '125px'})),
            ColumnDefinition.makeStaticColumn('creationDate', 'Date de la première opération d\'entrée', DateService.handleDateWithTime,
              () => ({'width': '125px'}))
          ];
          this.referentialPath = 'admin/agencies/accessionRegister';
          this.referentialIdentifier = 'OriginatingAgency';
          newBreadcrumb = [
            {label: 'Recherche', routerLink: ''},
            {label: 'Recherche par service producteur', routerLink: ''}
          ];
          break;
        default:
          this.router.navigate(['ingest/sip']);
      }
      if (this.isImportable) {
        this.searchButtonLabel =  'Accèder à l\'import des référentiels';
      }

      if (newBreadcrumb.length == 0) {
        newBreadcrumb = [
          {label: 'Administration', routerLink: ''},
          {label: this.breadcrumbName, routerLink: 'admin/search/' + this.referentialType}
        ];

      }
      this.setBreadcrumb(newBreadcrumb);
      this.setTitle(this.specificTitle);

      this.searchReferentialsService.getResults(this.searchForm).subscribe(
        data => {
            if (!!data && !!data.$results && this.initialSortKey != null) 
            {
                SearchReferentialsComponent.doInitialSort(data.$results, this.initialSortKey);
            }
            this.response = data;
        },
        error => console.log('Error - ', this.response)
      );

    });
  }

  static doInitialSort(items, sortKey) {
    let comparer = (a, b) => {
      return a[sortKey].trim().toLowerCase().localeCompare(b[sortKey].trim().toLowerCase());
    };
    items.sort(comparer);
  }

  static getValue(item): number {
    switch (item.RuleMeasurement.toUpperCase()) {
      case "YEAR":
        return item.RuleDuration * 365;

      case "MONTH":
        return item.RuleDuration * 31;
      default:
        return item.RuleDuration;
    }
  }

  public sortableDuration(items, event): void {
    let comparer = (a, b) => {
      return event.order * (SearchReferentialsComponent.getValue(a) - SearchReferentialsComponent.getValue(b));
    };
    items.sort(comparer);
  }


  pageOnInit() {
  }

  static downloadProfil(item, searchReferentialsService) {
    searchReferentialsService.downloadProfile(item.Identifier);
  }

  static checkProfil(item): boolean {
    return item.Path;
  }

  static downloadArchiveUnitProfile(item, searchReferentialsService) {
    searchReferentialsService.downloadArchiveUnitProfile(item.Identifier);
  }

  static checkArchiveUnitProfile(item): boolean {
    //TODO: When feature to retrieve DT has been set up check whether file exists or not.
    return false;
  }

  public preSearchFunction(request): Preresult {
    let preResult = new Preresult();
    preResult.request = request;
    preResult.searchProcessSkip = false;
    preResult.success = true;
    return preResult;
  }

  onNotify(event) {
    this.response = event.response;
    this.searchForm = event.form;
  }

  public initialSearch(service: any, responseEvent: EventEmitter<any>, form: any, offset) {
    service.getResults(form).subscribe(
      (response) => {
        responseEvent.emit({response: response, form: form});
      },
      (error) => responseEvent.emit({response: null, form: form})
    );
  }

  static ruleToLabel(rulesCategories) {
    return function (item) {
      let rules = rulesCategories
        .filter(x => x.rule === item)
        .map(x => x.label);
      if (rules.length === 1) {
        return rules[0];
      }
      return '';
    };
  }

  static handleStatus(status): string {
    return (status === 'ACTIVE' || status === 'true') ? 'Actif' : 'Inactif';
  }

  static appendUnitToRuleDuration(item): string {
    if (item.RuleMeasurement) {
      switch (item.RuleMeasurement.toUpperCase()) {
        case "YEAR":
          return item.RuleDuration <= 1 ? item.RuleDuration + ' année' : item.RuleDuration + ' années';
        case "MONTH":
          return item.RuleDuration + ' mois';
        case "DAY":
          return item.RuleDuration <= 1 ? item.RuleDuration + ' jour' : item.RuleDuration + ' jours';
        default :
          return item.RuleDuration;
      }
    } else {
      return item.RuleDuration;
    }
  }

  static checkAccessContract(item): string[] {
    if (item.Permissions instanceof Array) {
      for (let pem in item.Permissions) {
        if (item.Permissions[pem].AccessContracts && item.Permissions[pem].AccessContracts.length > 0) {
          return ['fa-check'];
        }
      }
    }
    return ['fa-close greyColor'];
  }

  static checkIngestContract(item): string[] {
    if (item.Permissions instanceof Array) {
      for (let pem in item.Permissions) {
        if (item.Permissions[pem].IngestContracts && item.Permissions[pem].IngestContracts.length > 0) {
          return ['fa-check'];
        }
      }
    }
    return ['fa-close greyColor'];
  }

  onNotifyPanelButton() {
    this.router.navigate(['admin/import/' + this.referentialType]);
  }

  public paginationSearch(service: any, offset) {
    return service.getResults(this.searchForm, offset);
  }
}
